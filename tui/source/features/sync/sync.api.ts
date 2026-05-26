import {
  API_BASE_URL,
  type SseTaskStartEvent,
  parseSseError,
  consumeSseStream,
  type SseTaskStageProgress,
  type SseTaskErrorEvent,
} from '#api/client.js';

export type SyncStreamHandlers = {
  onStart?: (event: SseTaskStartEvent) => void;
  onProgress?: (event: SseTaskStageProgress) => void;
  onResult?: () => void;
  onError?: (event: SseTaskErrorEvent) => void;
  signal?: AbortSignal;
};

export async function syncWithProgress(handlers: SyncStreamHandlers = {}): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/sync`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
    },
    signal: handlers.signal,
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`API ${response.status} ${response.statusText}: ${errorBody || 'No response body'}`);
  }

  let hasResult = false;

  const handleEvent = (eventName: string, payload: unknown): void => {
    if (eventName === 'start') {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
      handlers.onStart?.(payload as SseTaskStartEvent);
      return;
    }

    if (eventName === 'progress') {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
      handlers.onProgress?.(payload as SseTaskStageProgress);
      return;
    }

    if (eventName === 'result') {
      hasResult = true;
      handlers.onResult?.();
      return;
    }

    if (eventName === 'error') {
      const errorEvent = parseSseError(payload, 'SOURCE_SYNC_FAILED', 'Unknown sync SSE error');
      handlers.onError?.(errorEvent);
      throw new Error(`${errorEvent.code}: ${errorEvent.message}`);
    }
  };

  await consumeSseStream(response, handleEvent);

  if (!hasResult) {
    throw new Error('Sync SSE stream ended without a result event.');
  }
}

export async function syncAll(): Promise<void> {
  return syncWithProgress();
}
