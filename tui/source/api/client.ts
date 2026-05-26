import process from 'node:process';

const DEFAULT_API_BASE_URL = 'http://localhost:8081/api';

export const API_BASE_URL = process.env['LLMCR_API_BASE_URL'] ?? DEFAULT_API_BASE_URL;

export type SseTaskStartEvent = {
  name: string;
  id: string;
};

export type SseTaskStageProgress = {
  isError: boolean;
  stage: string;
  message: string;
};

export type SseTaskErrorEvent = {
  code: string;
  message: string;
};

export type SseTaskEvent = {
  taskId: string;
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

export function parseSseError(payload: unknown, fallbackCode: string, fallbackMessage: string): SseTaskErrorEvent {
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

export async function consumeSseStream(
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

export function requireNonBlank(value: string, message: string): void {
  if (!value || value.trim().length === 0) {
    throw new Error(message);
  }
}

export function requireNonEmpty(values: string[], message: string): void {
  if (!Array.isArray(values) || values.length === 0) {
    throw new Error(message);
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
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

export async function cancelSseTask(taskId: string): Promise<void> {
  requireNonBlank(taskId, 'taskId must not be blank');
  await apiRequest<void>(`/cancel/${encodeURIComponent(taskId)}`, {
    method: 'POST',
  });
}

export async function health(): Promise<string> {
  return apiRequest<string>('/health');
}
