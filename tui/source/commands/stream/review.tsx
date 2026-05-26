import { useState, useEffect } from 'react';
import { Box, Text } from 'ink';
import { useSseTaskLifecycle } from './hooks/use-sse-task-lifecycle.js';
import { ArgInput } from './components/arg-input.js';
import { type CommandProps } from '#commands/types.js';
import { LoadingSpinner } from '#components/loading-spinner.js';
import { cancelReviewTask, type CodeReviewOutput, reviewWithProgress } from '#api.js';

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

  // ─── 第二階段視圖：串流日誌與結果渲染（這裡 TypeScript 會完美自動推導 args 絕對有值） ───
  const { diffPath: activePath, useMock: activeMock } = args;

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
        {progressLogs.map((log, index) => (
          <Text key={index} color="gray">
            {log}
          </Text>
        ))}
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
          <Text color="white">Good Points: {reviewResult.reviewReport.mainReport.goodPoints.length}</Text>
          <Text color="white">Bad Points: {reviewResult.reviewReport.mainReport.badPoints.length}</Text>
          <Text color="white">
            Implementation Files: {reviewResult.reviewReport.mainReport.implementationDetails.length}
          </Text>
          <Text color="white">Checklist Items: {reviewResult.reviewReport.itemAnswers.length}</Text>
          <Text color="white">Issues: {reviewResult.reviewReport.mainReport.issues.length}</Text>

          {reviewResult.reviewReport.mainReport.issues.length > 0 && (
            <Box flexDirection="column" marginTop={1}>
              <Text color="cyan">Issue Preview:</Text>
              {reviewResult.reviewReport.mainReport.issues.slice(0, MAX_ISSUE_PREVIEW_COUNT).map((issue, index) => (
                <Text key={`issue-${index}`} color="gray">
                  {`${index + 1}. [${issue.type}] ${issue.title}${issue.location ? ` @ ${issue.location}` : ''}`}
                </Text>
              ))}
              {reviewResult.reviewReport.mainReport.issues.length > MAX_ISSUE_PREVIEW_COUNT && (
                <Text color="gray">
                  ... and {reviewResult.reviewReport.mainReport.issues.length - MAX_ISSUE_PREVIEW_COUNT} more issues
                </Text>
              )}
            </Box>
          )}
        </Box>
      )}
    </Box>
  );
};
