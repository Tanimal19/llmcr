import { useEffect } from 'react';
import { Box, Text } from 'ink';
import { type CommandProps } from '../types.js';
import { cancelSseTask, lsdb, syncWithProgress } from '../api.js';
import { ThinkingSpinner } from '../components/thinking-spinner.js';
import { useSseTaskLifecycle } from './use-sse-task-lifecycle.js';

export const SyncCommand = ({ onBack }: CommandProps) => {
  const {
    stageMessage,
    status,
    errorMessage,
    awaitingExitConfirm,
    progressLogs,
    setStageMessage,
    appendLog,
    startRun,
    handleTaskStart,
    handleProgress,
    handleError,
    completeRun,
    handleRunFailure,
    cleanupRun,
  } = useSseTaskLifecycle({
    initialStageMessage: 'Waiting to start sync...',
    cancellingStageMessage: 'Cancelling sync...',
    cancelledStageMessage: 'Sync cancelled by user',
    failedStageMessage: 'Sync failed',
    taskLabelLower: 'sync',
    taskLabelTitle: 'Sync',
    onBack,
    cancelTask: cancelSseTask,
  });

  useEffect(() => {
    const abortController = startRun('[INFO] Sync started');

    void lsdb()
      .then(trackRoots => {
        const unsynced = trackRoots.filter(trackRoot => !trackRoot.isSynced).length;
        appendLog(`[INFO] Track roots before sync: ${trackRoots.length} total, ${unsynced} unsynced`);
      })
      .catch((error: unknown) => {
        appendLog(`[WARN] Failed to load pre-sync preview: ${error instanceof Error ? error.message : String(error)}`);
      });

    syncWithProgress({
      onStart(event) {
        handleTaskStart(event, startedEvent => {
          setStageMessage(`Sync task started: ${startedEvent.name}`);
        });
      },
      onProgress: handleProgress,
      onError: handleError,
      onResult() {
        completeRun('Sync completed successfully', '[DONE] Sync completed successfully');

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
      handleRunFailure(error, abortController, '[INFO] Sync stream aborted');
    });

    return () => {
      cleanupRun(abortController);
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
