import { useState, useEffect, useRef } from 'react';
import { Box, Text, useInput } from 'ink';
import { type CommandProps } from '../types.js';
import { ThinkingSpinner } from '../components/thinkingSpinner.js';
import {
  cancelReviewTask,
  type CodeReviewOutput,
  reviewWithProgress,
  type ReviewErrorEvent,
  type ReviewStageProgress,
  type ReviewTaskEvent,
} from '../api.js';

type ReviewCommandProps = {
  diffPath?: string;
  useMock?: boolean;
} & CommandProps;

const MAX_ISSUE_PREVIEW_COUNT = 5;

export const ReviewCommand = ({ onBack, diffPath, useMock = false }: ReviewCommandProps) => {
  const [stageMessage, setStageMessage] = useState('Waiting to start review...');
  const [status, setStatus] = useState<'running' | 'success' | 'error'>('running');
  const [reviewResult, setReviewResult] = useState<CodeReviewOutput | undefined>(undefined);
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
  const [awaitingExitConfirm, setAwaitingExitConfirm] = useState(false);
  const [progressLogs, setProgressLogs] = useState<string[]>([]);
  const abortControllerRef = useRef<AbortController | undefined>(null);
  const reviewTaskIdRef = useRef<string | undefined>(null);
  const hasRequestedCancelRef = useRef(false);

  const appendLog = (message: string): void => {
    setProgressLogs(previous => [...previous, message]);
  };

  // Allow user to leave the view with ESC.
  useInput((_, key) => {
    if (!key.escape) {
      return;
    }

    if (status === 'running' && !hasRequestedCancelRef.current) {
      hasRequestedCancelRef.current = true;
      setAwaitingExitConfirm(true);
      setStageMessage('Cancelling review...');
      appendLog('[INFO] ESC pressed. Cancelling review task...');
      appendLog('[INFO] Press ESC again to return to the menu.');

      const taskId = reviewTaskIdRef.current;
      if (taskId) {
        void cancelReviewTask(taskId).catch(error => {
          appendLog(
            `[WARN] Failed to cancel review task on backend: ${error instanceof Error ? error.message : String(error)}`,
          );
        });
      } else {
        appendLog('[WARN] Review task id not received yet; only local stream abort will be applied.');
      }

      abortControllerRef.current?.abort();
      return;
    }

    if (awaitingExitConfirm) {
      onBack();
      return;
    }

    onBack();
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

    appendLog('[INFO] Review started');

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    const updateTask = (event: ReviewTaskEvent): void => {
      if (!event?.taskId) {
        return;
      }

      reviewTaskIdRef.current = event.taskId;
      appendLog(`[INFO] Review task registered: ${event.taskId}`);
    };

    const updateProgress = (event: ReviewStageProgress): void => {
      setStageMessage(`${event.stage} (${event.status}) - ${event.message}`);
      appendLog(`[${event.stage}] ${event.status} - ${event.message}`);
    };

    const updateError = (event: ReviewErrorEvent): void => {
      setStatus('error');
      setErrorMessage(`${event.code}: ${event.message}`);
      setStageMessage('Review failed');
      appendLog(`[ERROR] ${event.code}: ${event.message}`);
    };

    reviewWithProgress(diffPath ?? '', {
      onTask: updateTask,
      onProgress: updateProgress,
      onError: updateError,
      useMock,
      onResult(result) {
        setReviewResult(result);
        setStatus('success');
        setStageMessage('Review completed successfully');
        appendLog('[DONE] Review completed successfully');
      },
      signal: abortController.signal,
    }).catch(error => {
      if (abortController.signal.aborted) {
        setStatus('error');
        setErrorMessage(undefined);
        setStageMessage('Review cancelled by user');
        appendLog('[INFO] Review stream aborted');
        return;
      }

      setStatus('error');
      setErrorMessage(error instanceof Error ? error.message : String(error));
      setStageMessage('Review failed');
      appendLog(`[ERROR] ${error instanceof Error ? error.message : String(error)}`);
    });

    return () => {
      abortControllerRef.current = null;
      abortController.abort();
    };
  }, [diffPath, useMock]);

  return (
    <Box flexDirection="column" padding={1}>
      {diffPath && <Text color="yellow">Preforming code reivew on: {diffPath}</Text>}
      {useMock && <Text color="yellow">Preforming code review using Mock Data</Text>}
      <Text color="gray">(Press esc to cancel)</Text>

      {status === 'running' ? (
        <ThinkingSpinner message={stageMessage} color="white" />
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
