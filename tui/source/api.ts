import process from 'node:process';

const DEFAULT_API_BASE_URL = 'http://localhost:8081/api';

export const API_BASE_URL = (process.env['LLMCR_API_BASE_URL'] ?? DEFAULT_API_BASE_URL).replace(/\/+$/v, '');

export type ChatResponse = {
  answer: string;
  retrievedContexts: Record<string, number>;
};

export type CodeReviewIssue = {
  type: string;
  title: string;
  location: string;
  detail: string;
};

export type CodeReviewImplementationDetails = {
  filename: string;
  details: string[];
};

export type CodeReviewSummary = {
  motivation: string;
  goodPoints: string[];
  badPoints: string[];
  suggestion: string;
  implementationDetails: CodeReviewImplementationDetails[];
  issues: CodeReviewIssue[];
};

export type CodeReviewInterpretation = {
  changeDescription: string;
  changeMotivation: string;
};

export type CodeReviewEvidenceItem = {
  file: string;
  lines: string;
  reason: string;
};

export type CodeReviewAnswer = {
  finalAnswer: string;
  analysis: string;
  evidence: CodeReviewEvidenceItem[];
};

export type CodeReviewItemAnswer = {
  checklistItemTitle: string;
  answer: CodeReviewAnswer;
};

export type CodeReviewReport = {
  prId: number;
  prTitle: string;
  mainReport: CodeReviewSummary;
  interpretation: CodeReviewInterpretation;
  itemAnswers: CodeReviewItemAnswer[];
};

export type CodeReviewOutput = {
  reviewReport: CodeReviewReport;
  reportPath: string;
};

export type SseTaskStartEvent = {
  name: string;
  id: string;
};

export type ReviewStageProgress = {
  isError: boolean;
  stage: string;
  message: string;
};

export type ReviewErrorEvent = {
  code: string;
  message: string;
};

export type ReviewTaskEvent = {
  taskId: string;
};

export type ReviewStreamHandlers = {
  onStart?: (event: SseTaskStartEvent) => void;
  onTask?: (event: ReviewTaskEvent) => void;
  onProgress?: (event: ReviewStageProgress) => void;
  onResult?: (result: CodeReviewOutput) => void;
  onError?: (event: ReviewErrorEvent) => void;
  useMock?: boolean;
  signal?: AbortSignal;
};

export type SyncStreamHandlers = {
  onStart?: (event: SseTaskStartEvent) => void;
  onProgress?: (event: ReviewStageProgress) => void;
  onResult?: () => void;
  onError?: (event: ReviewErrorEvent) => void;
  signal?: AbortSignal;
};

export type SyncStatus = 'SYNCED' | 'REMOVED' | 'MODIFIED' | 'ADDED';

export type SourcePreview = {
  id: number | undefined;
  path: string;
  type: string;
  syncStatus: SyncStatus;
};

export type TrackRootPreview = {
  id: number;
  path: string;
  isSynced: boolean;
  lastSyncTime: string | undefined;
  sources: SourcePreview[];
};

export type InfoResponse = {
  configPath: string;
  config: unknown;
  lastSyncTime: string | undefined;
};

function parseSseEvent(rawEvent: string): { event: string; data: string } {
  const lines = rawEvent.split(/\r?\n/v);
  let eventName = 'message';
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim();
      continue;
    }

    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim());
    }
  }

  return { event: eventName, data: dataLines.join('\n') };
}

function parseJsonPayload(payload: string): unknown {
  if (!payload) {
    return undefined;
  }

  try {
    return JSON.parse(payload);
  } catch {
    return payload;
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function extractCode(payloadRecord: Record<string, unknown>): string | undefined {
  const { errorCode, code: rawCode } = payloadRecord;

  if (isObject(errorCode)) {
    const { code } = errorCode;
    if (typeof code === 'string' && code.trim().length > 0) {
      return code;
    }
  }

  if (typeof rawCode === 'string' && rawCode.trim().length > 0) {
    return rawCode;
  }

  return undefined;
}

function parseSseError(payload: unknown, fallbackCode: string, fallbackMessage: string): ReviewErrorEvent {
  if (isObject(payload)) {
    // 使用物件解構規範修復 prefer-destructuring
    const { message: rawMessage } = payload;
    const message = typeof rawMessage === 'string' && rawMessage.trim().length > 0 ? rawMessage : fallbackMessage;

    const code = extractCode(payload) ?? fallbackCode;
    return { code, message };
  }

  if (typeof payload === 'string' && payload.trim().length > 0) {
    return { code: fallbackCode, message: payload };
  }

  return {
    code: fallbackCode,
    message: fallbackMessage,
  };
}

async function consumeSseStream(
  response: Response,
  handleEvent: (eventName: string, payload: unknown) => void,
): Promise<void> {
  if (!response.body) {
    throw new Error('SSE response body is not available.');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    // eslint-disable-next-line no-await-in-loop
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split(/\r?\n\r?\n/v);
    buffer = chunks.pop() ?? '';

    for (const chunk of chunks) {
      if (!chunk.trim()) {
        continue;
      }

      const parsed = parseSseEvent(chunk);
      const payload = parseJsonPayload(parsed.data);
      handleEvent(parsed.event, payload);
    }
  }

  if (buffer.trim()) {
    const parsed = parseSseEvent(buffer);
    const payload = parseJsonPayload(parsed.data);
    handleEvent(parsed.event, payload);
  }
}

function requireNonBlank(value: string, message: string): void {
  if (!value || value.trim().length === 0) {
    throw new Error(message);
  }
}

function requireNonEmpty(values: string[], message: string): void {
  if (!Array.isArray(values) || values.length === 0) {
    throw new Error(message);
  }
}

async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  if (typeof fetch !== 'function') {
    throw new TypeError('Global fetch is not available in this runtime. Use Node.js 18+ or provide a fetch polyfill.');
  }

  const headers = new Headers(init?.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json, text/plain');
  }

  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`API ${response.status} ${response.statusText}: ${errorBody || 'No response body'}`);
  }

  if (response.status === 204) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
    return (await response.json()) as T;
  }

  // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
  return (await response.text()) as T;
}

export async function health(): Promise<string> {
  return apiRequest<string>('/health');
}

export async function info(): Promise<InfoResponse> {
  return apiRequest<InfoResponse>('/info');
}

export async function chat(query: string): Promise<ChatResponse> {
  requireNonBlank(query, 'query must not be blank');
  return apiRequest<ChatResponse>('/chat', {
    method: 'POST',
    body: JSON.stringify({ query }),
  });
}

export async function getRagScope(): Promise<Record<string, boolean>> {
  return apiRequest<Record<string, boolean>>('/getrag', {
    method: 'POST',
  });
}

export async function setRagScope(trackRootPaths: string[]): Promise<void> {
  requireNonEmpty(trackRootPaths, 'trackRootPaths must not be empty');
  await apiRequest<void>('/setrag', {
    method: 'POST',
    body: JSON.stringify({ trackRootPaths }),
  });
}

export type CodeReviewInput = {
  jsonFilePath: string;
  useMockData?: boolean;
};

export async function review(input: CodeReviewInput): Promise<CodeReviewOutput> {
  return reviewWithProgress(input);
}

export async function reviewWithProgress(
  input: CodeReviewInput,
  handlers: ReviewStreamHandlers = {},
): Promise<CodeReviewOutput> {
  const response = await fetch(`${API_BASE_URL}/review`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      jsonFilePath: input.jsonFilePath,
      useMockData: input.useMockData ?? handlers.useMock ?? false,
    }),
    signal: handlers.signal,
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`API ${response.status} ${response.statusText}: ${errorBody || 'No response body'}`);
  }

  let finalResult: CodeReviewOutput | undefined;

  const handleEvent = (eventName: string, payload: unknown): void => {
    if (eventName === 'start') {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
      const startEvent = payload as SseTaskStartEvent;
      handlers.onStart?.(startEvent);
      if (startEvent && typeof startEvent.id === 'string') {
        handlers.onTask?.({ taskId: startEvent.id });
      }

      return;
    }

    if (eventName === 'task') {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
      handlers.onTask?.(payload as ReviewTaskEvent);
      return;
    }

    if (eventName === 'progress') {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
      handlers.onProgress?.(payload as ReviewStageProgress);
      return;
    }

    if (eventName === 'result') {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
      finalResult = payload as CodeReviewOutput;
      handlers.onResult?.(finalResult);
      return;
    }

    if (eventName === 'error') {
      const errorEvent = parseSseError(payload, 'REVIEW_PIPELINE_FAILED', 'Unknown review SSE error');
      handlers.onError?.(errorEvent);
      throw new Error(`${errorEvent.code}: ${errorEvent.message}`);
    }
  };

  await consumeSseStream(response, handleEvent);

  if (finalResult === undefined) {
    throw new Error('Review SSE stream ended without a result event.');
  }

  return finalResult;
}

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
      handlers.onProgress?.(payload as ReviewStageProgress);
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

export async function lsdb(): Promise<TrackRootPreview[]> {
  return apiRequest<TrackRootPreview[]>('/lsdb');
}

export async function cancelSseTask(taskId: string): Promise<void> {
  requireNonBlank(taskId, 'taskId must not be blank');
  await apiRequest<void>(`/cancel/${encodeURIComponent(taskId)}`, {
    method: 'POST',
  });
}

export async function cancelReviewTask(taskId: string): Promise<void> {
  return cancelSseTask(taskId);
}

export async function syncAll(): Promise<void> {
  return syncWithProgress();
}
