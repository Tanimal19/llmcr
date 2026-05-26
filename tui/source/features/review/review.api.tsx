import {
  API_BASE_URL,
  type SseTaskStartEvent,
  cancelSseTask,
  parseSseError,
  consumeSseStream,
  type SseTaskStageProgress,
  type SseTaskErrorEvent,
  type SseTaskEvent,
} from '#api/client.js';

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

export type ReviewStreamHandlers = {
  onStart?: (event: SseTaskStartEvent) => void;
  onTask?: (event: SseTaskEvent) => void;
  onProgress?: (event: SseTaskStageProgress) => void;
  onResult?: (result: CodeReviewOutput) => void;
  onError?: (event: SseTaskErrorEvent) => void;
  useMock?: boolean;
  signal?: AbortSignal;
};

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
      handlers.onTask?.(payload as SseTaskEvent);
      return;
    }

    if (eventName === 'progress') {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
      handlers.onProgress?.(payload as SseTaskStageProgress);
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

export async function cancelReviewTask(taskId: string): Promise<void> {
  return cancelSseTask(taskId);
}
