import { useState, useEffect } from 'react';
import { Box, Text } from 'ink';
import { type CommandProps } from '#commands/types.js';
import { LoadingSpinner } from '#components';
import { cancelReviewTask, type CodeReviewOutput, reviewWithProgress } from '#api.js';
import { useSseTaskLifecycle } from './hooks/use-sse-task-lifecycle.js';

type ReviewCommandProps = {
  diffPath?: string;
  useMock?: boolean;
} & CommandProps;

const MAX_ISSUE_PREVIEW_COUNT = 5;

export const ReviewCommand = ({ onBack, diffPath, useMock = false }: ReviewCommandProps) => {
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
    if ((!diffPath || diffPath.trim().length === 0) && !useMock) {
      setStatus('error');
      setErrorMessage('Diff path is required.');
      setStageMessage('Review did not start');
      appendLog('[ERROR] Review did not start: Diff path is required.');
      return;
    }

    if (useMock) {
      setStageMessage('Using mock review data...');
      appendLog('[INFO] Using mock review data');
    }

    const abortController = startRun('[INFO] Review started');

    reviewWithProgress(
      {
        jsonFilePath: diffPath ?? '',
        useMockData: useMock,
      },
      {
        onStart: handleTaskStart,
        onProgress: handleProgress,
        onError: handleError,
        useMock,
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
  }, [diffPath, useMock]);

  // ─── 1. 預先建立日誌列元件陣列 ───
  const renderedLogs = [];
  for (const [i, progressLog] of progressLogs.entries()) {
    renderedLogs.push(
      <Text key={`review-progress-log-${i}`} color="gray">
        {progressLog}
      </Text>,
    );
  }

  // ─── 2. 預先建立 Issue 預覽列元件陣列 ───
  const renderedIssues = [];
  if (status === 'success' && reviewResult) {
    const issuesList = reviewResult.reviewReport.mainReport.issues;
    const previewCount = Math.min(issuesList.length, MAX_ISSUE_PREVIEW_COUNT);
    for (let i = 0; i < previewCount; i++) {
      const issue = issuesList[i];

      // 型別守衛
      if (!issue) {
        continue;
      }

      // 攤平巢狀模板字面量，符合 S4624
      const locationSuffix = issue.location ? ` @ ${issue.location}` : '';
      renderedIssues.push(
        <Text key={`review-issue-item-${i}`} color="gray">
          {`${i + 1}. [${issue.type}] ${issue.title}${locationSuffix}`}
        </Text>,
      );
    }
  }

  return (
    <Box flexDirection="column" padding={1}>
      {diffPath && <Text color="yellow">Performing code review on: {diffPath}</Text>}
      {useMock && <Text color="yellow">Performing code review using mock data</Text>}
      <Text color="gray">(Press esc to cancel)</Text>

      {status === 'running' ? (
        <LoadingSpinner message={stageMessage} color="white" />
      ) : (
        <Text color={status === 'error' ? 'red' : 'white'}>{stageMessage}</Text>
      )}
      <Box flexDirection="column" marginTop={1}>
        <Text color="cyan">Progress Log:</Text>
        {progressLogs.length === 0 && <Text color="gray">(no events yet)</Text>}
        {renderedLogs}
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
              {renderedIssues}
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
