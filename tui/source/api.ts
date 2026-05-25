import process from 'node:process';

const DEFAULT_API_BASE_URL = 'http://localhost:8081/api';

export const API_BASE_URL = (process.env['LLMCR_API_BASE_URL'] ?? DEFAULT_API_BASE_URL).replace(/\/+$/, '');

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

export type ReviewStageProgress = {
  stage: string;
  status: string;
  current: number;
  total: number;
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
  onTask?: (event: ReviewTaskEvent) => void;
  onProgress?: (event: ReviewStageProgress) => void;
  onResult?: (result: CodeReviewOutput) => void;
  onError?: (event: ReviewErrorEvent) => void;
  useMock?: boolean;
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
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return (await response.json()) as T;
  }

  return (await response.text()) as T;
}

export async function health(): Promise<string> {
  return apiRequest<string>('/health');
}

export async function chat(query: string): Promise<ChatResponse> {
  requireNonBlank(query, 'query must not be blank');
  return apiRequest<ChatResponse>('/chat', {
    method: 'POST',
    body: JSON.stringify({ query }),
  });
}

export async function getRagScope(): Promise<Record<string, boolean>> {
  return apiRequest<Record<string, boolean>>('/rag-scope');
}

export async function setRagScope(trackRootPaths: string[]): Promise<void> {
  requireNonEmpty(trackRootPaths, 'trackRootPaths must not be empty');
  await apiRequest<void>('/rag-scope', {
    method: 'POST',
    body: JSON.stringify({ trackRootPaths }),
  });
}

export async function review(pullRequestJsonPath: string): Promise<CodeReviewOutput> {
  return reviewWithProgress(pullRequestJsonPath);
}

export async function reviewWithProgress(
  pullRequestJsonPath: string,
  handlers: ReviewStreamHandlers = {},
): Promise<CodeReviewOutput> {
  const response = await fetch(`${API_BASE_URL}/review`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ pullRequestJsonPath, useMock: handlers.useMock ?? false }),
    signal: handlers.signal,
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`API ${response.status} ${response.statusText}: ${errorBody || 'No response body'}`);
  }

  if (!response.body) {
    throw new Error('SSE response body is not available.');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let finalResult: CodeReviewOutput | undefined;

  const parseSseEvent = (rawEvent: string): { event: string; data: string } => {
    const lines = rawEvent.split(/\r?\n/);
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
  };

  const parseJson = (payload: string): unknown => {
    if (!payload) {
      return null;
    }

    try {
      return JSON.parse(payload);
    } catch {
      return payload;
    }
  };

  const handleEvent = (eventName: string, payload: unknown): void => {
    if (eventName === 'task') {
      handlers.onTask?.(payload as ReviewTaskEvent);
      return;
    }

    if (eventName === 'progress') {
      handlers.onProgress?.(payload as ReviewStageProgress);
      return;
    }

    if (eventName === 'result') {
      finalResult = payload as CodeReviewOutput;
      handlers.onResult?.(finalResult);
      return;
    }

    if (eventName === 'error') {
      const errorEvent = (payload as ReviewErrorEvent) ?? {
        code: 'REVIEW_PIPELINE_FAILED',
        message: 'Unknown review SSE error',
      };
      handlers.onError?.(errorEvent);
      throw new Error(`${errorEvent.code}: ${errorEvent.message}`);
    }
  };

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split(/\r?\n\r?\n/);
    buffer = chunks.pop() ?? '';

    for (const chunk of chunks) {
      if (!chunk.trim()) {
        continue;
      }

      const parsed = parseSseEvent(chunk);
      const payload = parseJson(parsed.data);
      handleEvent(parsed.event, payload);
    }
  }

  if (buffer.trim()) {
    const parsed = parseSseEvent(buffer);
    const payload = parseJson(parsed.data);
    handleEvent(parsed.event, payload);
  }

  if (finalResult === undefined) {
    throw new Error('Review SSE stream ended without a result event.');
  }

  return finalResult;
}

export async function lsdb(): Promise<TrackRootPreview[]> {
  return apiRequest<TrackRootPreview[]>('/lsdb');
}

export async function cancelReviewTask(taskId: string): Promise<void> {
  requireNonBlank(taskId, 'taskId must not be blank');
  await apiRequest<void>(`/review/${encodeURIComponent(taskId)}/cancel`, {
    method: 'POST',
  });
}

export async function syncAll(): Promise<void> {
  await apiRequest<void>('/sync', {
    method: 'POST',
  });
}
