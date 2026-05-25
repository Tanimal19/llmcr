import { useEffect } from 'react';
import { Box, Text } from 'ink';
import { type CommandProps } from '../types.js';
import { cancelSseTask, lsdb, syncWithProgress, type TrackRootPreview } from '../api.js';
import { LoadingSpinner } from '../components/loading-spinner.js';
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

  const logTrackRootsPreview = async (stage: 'before' | 'after') => {
    try {
      const trackRoots: TrackRootPreview[] = await lsdb();
      const unsynced = trackRoots.filter(trackRoot => !trackRoot.isSynced).length;
      appendLog(`[INFO] Track roots ${stage} sync: ${trackRoots.length} total, ${unsynced} unsynced`);
    } catch (error: unknown) {
      appendLog(
        `[WARN] Failed to load ${stage}-sync preview: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  };

  const handleSyncStart = (event: any) => {
    handleTaskStart(event, startedEvent => {
      setStageMessage(`Sync task started: ${startedEvent.name}`);
    });
  };

  useEffect(() => {
    const abortController = startRun('[INFO] Sync started');

    // 執行同步前預覽
    void logTrackRootsPreview('before');

    syncWithProgress({
      onStart: handleSyncStart,
      onProgress: handleProgress,
      onError: handleError,
      onResult() {
        completeRun('Sync completed successfully', '[DONE] Sync completed successfully');
        // 執行同步後預覽
        void logTrackRootsPreview('after');
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
      {status === 'success' && <Text color="green">Sync done. Press ESC to return.</Text>}
      {status === 'error' && errorMessage && <Text color="red">Error: {errorMessage}</Text>}
    </Box>
  );
};
