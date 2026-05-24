import process from 'node:process';

const DEFAULT_API_BASE_URL = 'http://localhost:8081/api';

export const API_BASE_URL = (process.env['LLMCR_API_BASE_URL'] ?? DEFAULT_API_BASE_URL).replace(/\/+$/, '');

export type ChatResponse = {
  answer: string;
  retrievedContexts: Record<string, number>;
};

export type CodeReviewOutput = Record<string, unknown>;

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

function requireTrackRootId(trackRootId: number): void {
  if (!Number.isInteger(trackRootId) || trackRootId <= 0) {
    throw new Error('trackRootId must be a positive integer');
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
  requireNonBlank(pullRequestJsonPath, 'pullRequestJsonPath must not be blank');
  return apiRequest<CodeReviewOutput>('/review', {
    method: 'POST',
    body: JSON.stringify({ pullRequestJsonPath }),
  });
}

export async function lsdb(): Promise<TrackRootPreview[]> {
  return apiRequest<TrackRootPreview[]>('/lsdb');
}

export async function syncAll(): Promise<void> {
  await apiRequest<void>('/sync', {
    method: 'POST',
  });
}

export async function syncByTrackRootId(trackRootId: number): Promise<void> {
  requireTrackRootId(trackRootId);
  await apiRequest<void>(`/sync/${trackRootId}`, {
    method: 'POST',
  });
}
