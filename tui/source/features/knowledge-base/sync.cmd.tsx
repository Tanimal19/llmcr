import { useEffect } from 'react';
import { Box, Text } from 'ink';
import { lsdb, type TrackRootPreview } from './lsdb.api.js';
import { syncWithProgress } from './sync.api.js';
import { useSseTaskLifecycle } from '#hooks';
import { type CommandProps } from '#features/types.js';
import { cancelSseTask, type SseTaskStartEvent } from '#api/client.js';
import { LoadingSpinner } from '#components';

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

  const handleSyncStart = (event: SseTaskStartEvent) => {
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

  // ─── 💡 修正 S6479：利用常規 for 迴圈預先建立日誌列元件陣列 ───
  const renderedLogs = [];
  for (const [i, progressLog] of progressLogs.entries()) {
    renderedLogs.push(
      <Text key={`sync-progress-log-${i}`} color="gray">
        {progressLog}
      </Text>,
    );
  }

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

        {/* 渲染預先生成的日誌列表 */}
        {renderedLogs}
      </Box>

      {awaitingExitConfirm && <Text color="yellow">Cancellation requested. Press ESC again to return.</Text>}
      {status === 'success' && <Text color="green">Sync done. Press ESC to return.</Text>}
      {status === 'error' && errorMessage && <Text color="red">Error: {errorMessage}</Text>}
    </Box>
  );
};
