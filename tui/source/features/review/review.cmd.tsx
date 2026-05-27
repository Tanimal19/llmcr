import { useState, useEffect } from 'react';
import { Box, Text } from 'ink';
import { cancelReviewTask, type CodeReviewOutput, reviewWithProgress } from './review.api.js';
import { useSseTaskLifecycle } from '#hooks';
import { type CommandProps } from '#features/types.js';
import { ArgInput, LoadingSpinner } from '#components';

// ─── 1. 獨立且乾淨的參數型態宣告 ───
type ReviewCommandProps = {
  diffPath?: string;
  useMock?: boolean;
} & CommandProps;

type ReviewArgs = {
  diffPath: string;
  useMock: boolean;
};

const MAX_ISSUE_PREVIEW_COUNT = 5;

export const ReviewCommand = ({ onBack, diffPath, useMock = false }: ReviewCommandProps) => {
  const [args, setArgs] = useState<ReviewArgs | undefined>(() => {
    if (diffPath !== undefined) {
      return { diffPath, useMock };
    }

    return undefined;
  });

  const [reviewResult, setReviewResult] = useState<CodeReviewOutput | undefined>(undefined);

  const {
    stageMessage,
    status,
    errorMessage,
    awaitingExitConfirm,
    progressLogs,
    setStageMessage,
    setStatus,
    setErrorMessage,
    appendLog,
    startRun,
    handleTaskStart,
    handleProgress,
    handleError,
    completeRun,
    handleRunFailure,
    cleanupRun,
  } = useSseTaskLifecycle({
    initialStageMessage: 'Waiting to start review...',
    cancellingStageMessage: 'Cancelling review...',
    cancelledStageMessage: 'Review cancelled by user',
    failedStageMessage: 'Review failed',
    taskLabelLower: 'review',
    taskLabelTitle: 'Review',
    onBack,
    cancelTask: cancelReviewTask,
  });

  useEffect(() => {
    // 💡 守衛條件：如果 args 是 undefined，代表使用者還在輸入，靜靜等待
    if (!args) {
      return;
    }

    const { diffPath: activePath, useMock: activeMock } = args;

    if ((!activePath || activePath.trim().length === 0) && !activeMock) {
      setStatus('error');
      setErrorMessage('Diff path is required.');
      setStageMessage('Review did not start');
      appendLog('[ERROR] Review did not start: Diff path is required.');
      return;
    }

    if (activeMock) {
      setStageMessage('Using mock review data...');
      appendLog('[INFO] Using mock review data');
    }

    const abortController = startRun('[INFO] Review started');

    reviewWithProgress(
      {
        jsonFilePath: activePath ?? '',
        useMockData: activeMock,
      },
      {
        onStart: handleTaskStart,
        onProgress: handleProgress,
        onError: handleError,
        useMock: activeMock,
        onResult(result) {
          setReviewResult(result);
          completeRun('Review completed successfully', '[DONE] Review completed successfully');
        },
        signal: abortController.signal,
      },
    ).catch((error: unknown) => {
      handleRunFailure(error, abortController, '[INFO] Review stream aborted');
    });

    return () => {
      cleanupRun(abortController);
    };
  }, [args]);

  // ─── 第一階段視圖：若 args 為 undefined，展示輸入畫面 ───
  if (!args) {
    return (
      <ArgInput
        title="Please enter the path to the pull request JSON file for review"
        placeholder="./example.diff (leave empty to use mock data)"
        usePlaceholderOnEmpty={false}
        onCancel={onBack}
        onSubmit={value => {
          setArgs({
            diffPath: value,
            useMock: value.length === 0,
          });
        }}
      />
    );
  }

  // ─── 第二階段視圖 ───
  const { diffPath: activePath, useMock: activeMock } = args;
  const reviewReport = reviewResult?.reviewReport;
  const summary = reviewReport?.content ?? reviewReport?.mainReport;
  const checklistItems = reviewReport?.checklistItems ?? reviewReport?.itemAnswers ?? [];
  const goodPointsCount = summary?.goodPoints?.length ?? 0;
  const badPointsCount = summary?.badPoints?.length ?? 0;
  const implementationFilesCount = summary?.implementationDetails?.length ?? 0;
  const issues = summary?.issues ?? [];

  // 用於動態生成穩定且不重複日誌 key 的計數器（每次 Render 重置）
  const logCounts = new Map<string, number>();

  return (
    <Box flexDirection="column" padding={1}>
      {activePath && <Text color="yellow">Performing code review on: {activePath}</Text>}
      {activeMock && <Text color="yellow">Performing code review using mock data</Text>}
      <Text color="gray">(Press esc to cancel)</Text>

      {status === 'running' ? (
        <LoadingSpinner message={stageMessage} color="white" />
      ) : (
        <Text color={status === 'error' ? 'red' : 'white'}>{stageMessage}</Text>
      )}

      <Box flexDirection="column" marginTop={1}>
        <Text color="cyan">Progress Log:</Text>
        {progressLogs.length === 0 && <Text color="gray">(no events yet)</Text>}
        {progressLogs.map(log => {
          // 計算當前字串在此輪渲染是第幾次出現，確保 key 絕對唯一且不依賴 Array index
          const currentCount = (logCounts.get(log) ?? 0) + 1;
          logCounts.set(log, currentCount);
          return (
            <Text key={`${log}-${currentCount}`} color="gray">
              {log}
            </Text>
          );
        })}
      </Box>

      {awaitingExitConfirm && <Text color="yellow">Cancellation requested. Press ESC again to return.</Text>}
      {status === 'success' && <Text color="green">Review done. Press ESC to return.</Text>}
      {status === 'error' && errorMessage && <Text color="red">Error: {errorMessage}</Text>}

      {status === 'success' && reviewResult && (
        <Box flexDirection="column" marginTop={1}>
          <Text color="cyan">Review Result Summary:</Text>
          <Text color="white">
            PR: #{reviewResult.reviewReport.prId} {reviewResult.reviewReport.prTitle}
          </Text>
          <Text color="white">Report Path: {reviewResult.reportPath}</Text>
          <Text color="white">Good Points: {goodPointsCount}</Text>
          <Text color="white">Bad Points: {badPointsCount}</Text>
          <Text color="white">Implementation Files: {implementationFilesCount}</Text>
          <Text color="white">Checklist Items: {checklistItems.length}</Text>
          <Text color="white">Issues: {issues.length}</Text>

          {issues.length > 0 && (
            <Box flexDirection="column" marginTop={1}>
              <Text color="cyan">Issue Preview:</Text>
              {issues.slice(0, MAX_ISSUE_PREVIEW_COUNT).map((issue, index) => {
                // 1. 將巢狀樣板字串提取至外部變數，修正 S4624
                const locationStr = issue.location ? ` @ ${issue.location}` : '';
                // 2. 使用業務欄位組合成唯一 key，修正 S6479
                const issueKey = `${issue.type}-${issue.title}-${issue.location}`;

                return (
                  <Text key={issueKey} color="gray">
                    {`${index + 1}. [${issue.type}] ${issue.title}${locationStr}`}
                  </Text>
                );
              })}
              {issues.length > MAX_ISSUE_PREVIEW_COUNT && (
                <Text color="gray">... and {issues.length - MAX_ISSUE_PREVIEW_COUNT} more issues</Text>
              )}
            </Box>
          )}
        </Box>
      )}
    </Box>
  );
};
