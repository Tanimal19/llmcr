import { useEffect, useRef, useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { type CommandProps } from '../types.js';
import {
  cancelSseTask,
  lsdb,
  syncWithProgress,
  type ReviewErrorEvent,
  type ReviewStageProgress,
  type SseTaskStartEvent,
} from '../api.js';
import { ThinkingSpinner } from '../components/thinking-spinner.js';

export const SyncCommand = ({ onBack }: CommandProps) => {
  const [stageMessage, setStageMessage] = useState('Waiting to start sync...');
  const [status, setStatus] = useState<'running' | 'success' | 'error'>('running');
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
  const [awaitingExitConfirm, setAwaitingExitConfirm] = useState(false);
  const [progressLogs, setProgressLogs] = useState<string[]>([]);
  const abortControllerRef = useRef<AbortController | undefined>(undefined);
  const syncTaskIdRef = useRef<string | undefined>(undefined);
  const hasRequestedCancelRef = useRef(false);
  const waitingTaskIdForCancelRef = useRef(false);

  const appendLog = (message: string): void => {
    setProgressLogs(previous => [...previous, message]);
  };

  useInput((_, key) => {
    if (!key.escape) {
      return;
    }

    if (status === 'running' && !hasRequestedCancelRef.current) {
      hasRequestedCancelRef.current = true;
      waitingTaskIdForCancelRef.current = true;
      setAwaitingExitConfirm(true);
      setStageMessage('Cancelling sync...');
      appendLog('[INFO] ESC pressed. Cancelling sync task...');
      appendLog('[INFO] Press ESC again to return to the menu.');

      const taskId = syncTaskIdRef.current;
      if (taskId) {
        waitingTaskIdForCancelRef.current = false;
        void cancelSseTask(taskId).catch((error: unknown) => {
          appendLog(
            `[WARN] Failed to cancel sync task on backend: ${error instanceof Error ? error.message : String(error)}`,
          );
        });
        abortControllerRef.current?.abort();
      } else {
        appendLog('[INFO] Waiting for task id from backend before sending cancel request...');
      }

      return;
    }

    onBack();
  });

  useEffect(() => {
    appendLog('[INFO] Sync started');

    void lsdb()
      .then(trackRoots => {
        const unsynced = trackRoots.filter(trackRoot => !trackRoot.isSynced).length;
        appendLog(`[INFO] Track roots before sync: ${trackRoots.length} total, ${unsynced} unsynced`);
      })
      .catch((error: unknown) => {
        appendLog(`[WARN] Failed to load pre-sync preview: ${error instanceof Error ? error.message : String(error)}`);
      });

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    const updateTask = (event: SseTaskStartEvent): void => {
      if (!event?.id) {
        return;
      }

      syncTaskIdRef.current = event.id;
      setStageMessage(`Sync task started: ${event.name}`);
      appendLog(`[INFO] Sync task started: ${event.name} (${event.id})`);

      if (waitingTaskIdForCancelRef.current) {
        waitingTaskIdForCancelRef.current = false;
        appendLog('[INFO] Sending cancellation request to backend...');
        void cancelSseTask(event.id).catch((error: unknown) => {
          appendLog(
            `[WARN] Failed to cancel sync task on backend: ${error instanceof Error ? error.message : String(error)}`,
          );
        });
        abortController.abort();
      }
    };

    const updateProgress = (event: ReviewStageProgress): void => {
      const level = event.isError ? 'ERROR' : 'INFO';
      setStageMessage(`${event.stage} - ${event.message}`);
      appendLog(`[${event.stage}] ${level} - ${event.message}`);
    };

    const updateError = (event: ReviewErrorEvent): void => {
      setStatus('error');
      setErrorMessage(`${event.code}: ${event.message}`);
      setStageMessage('Sync failed');
      appendLog(`[ERROR] ${event.code}: ${event.message}`);
    };

    syncWithProgress({
      onStart: updateTask,
      onProgress: updateProgress,
      onError: updateError,
      onResult() {
        setStatus('success');
        setStageMessage('Sync completed successfully');
        appendLog('[DONE] Sync completed successfully');

        void lsdb()
          .then(trackRoots => {
            const unsynced = trackRoots.filter(trackRoot => !trackRoot.isSynced).length;
            appendLog(`[INFO] Track roots after sync: ${trackRoots.length} total, ${unsynced} unsynced`);
          })
          .catch((error: unknown) => {
            appendLog(
              `[WARN] Failed to load post-sync preview: ${error instanceof Error ? error.message : String(error)}`,
            );
          });
      },
      signal: abortController.signal,
    }).catch((error: unknown) => {
      if (abortController.signal.aborted) {
        setStatus('error');
        setErrorMessage(undefined);
        setStageMessage('Sync cancelled by user');
        appendLog('[INFO] Sync stream aborted');
        return;
      }

      setStatus('error');
      setErrorMessage(error instanceof Error ? error.message : String(error));
      setStageMessage('Sync failed');
      appendLog(`[ERROR] ${error instanceof Error ? error.message : String(error)}`);
    });

    return () => {
      abortControllerRef.current = undefined;
      waitingTaskIdForCancelRef.current = false;
      abortController.abort();
    };
  }, []);

  return (
    <Box flexDirection="column" padding={1}>
      <Text color="yellow">Performing source sync using backend SSE stream</Text>
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
      {status === 'success' && <Text color="green">Sync done. Press ESC to return.</Text>}
      {status === 'error' && errorMessage && <Text color="red">Error: {errorMessage}</Text>}
    </Box>
  );
};
