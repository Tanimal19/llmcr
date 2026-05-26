import { useRef, useState } from 'react';
import { useInput } from 'ink';
import { type SseTaskErrorEvent, type SseTaskStageProgress, type SseTaskStartEvent } from '#api/client.js';

type TaskStatus = 'running' | 'success' | 'error';

type UseSseTaskLifecycleOptions = {
  initialStageMessage: string;
  cancellingStageMessage: string;
  cancelledStageMessage: string;
  failedStageMessage: string;
  taskLabelLower: string;
  taskLabelTitle: string;
  onBack: () => void;
  cancelTask: (taskId: string) => Promise<void>;
};

type UseSseTaskLifecycleResult = {
  stageMessage: string;
  status: TaskStatus;
  errorMessage: string | undefined;
  awaitingExitConfirm: boolean;
  progressLogs: string[];
  setStageMessage: (message: string) => void;
  setStatus: (status: TaskStatus) => void;
  setErrorMessage: (message: string | undefined) => void;
  appendLog: (message: string) => void;
  startRun: (startLogMessage: string) => AbortController;
  handleTaskStart: (event: SseTaskStartEvent, onStarted?: (event: SseTaskStartEvent) => void) => void;
  handleProgress: (event: SseTaskStageProgress) => void;
  handleError: (event: SseTaskErrorEvent) => void;
  completeRun: (successStageMessage: string, successLogMessage: string) => void;
  handleRunFailure: (error: unknown, abortController: AbortController, abortLogMessage: string) => void;
  cleanupRun: (abortController: AbortController) => void;
};

export function useSseTaskLifecycle(options: UseSseTaskLifecycleOptions): UseSseTaskLifecycleResult {
  const [stageMessage, setStageMessage] = useState(options.initialStageMessage);
  const [status, setStatus] = useState<TaskStatus>('running');
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
  const [awaitingExitConfirm, setAwaitingExitConfirm] = useState(false);
  const [progressLogs, setProgressLogs] = useState<string[]>([]);

  const abortControllerRef = useRef<AbortController | undefined>(undefined);
  const taskIdRef = useRef<string | undefined>(undefined);
  const hasRequestedCancelRef = useRef(false);
  const waitingTaskIdForCancelRef = useRef(false);

  const appendLog = (message: string): void => {
    setProgressLogs(previous => [...previous, message]);
  };

  const requestCancel = (taskId: string): void => {
    void options.cancelTask(taskId).catch((error: unknown) => {
      appendLog(
        `[WARN] Failed to cancel ${options.taskLabelLower} task on backend: ${error instanceof Error ? error.message : String(error)}`,
      );
    });
  };

  useInput((_, key) => {
    if (!key.escape) {
      return;
    }

    if (status === 'running' && !hasRequestedCancelRef.current) {
      hasRequestedCancelRef.current = true;
      waitingTaskIdForCancelRef.current = true;
      setAwaitingExitConfirm(true);
      setStageMessage(options.cancellingStageMessage);
      appendLog(`[INFO] ESC pressed. Cancelling ${options.taskLabelLower} task...`);
      appendLog('[INFO] Press ESC again to return to the menu.');

      const taskId = taskIdRef.current;
      if (taskId) {
        waitingTaskIdForCancelRef.current = false;
        requestCancel(taskId);
        abortControllerRef.current?.abort();
      } else {
        appendLog('[INFO] Waiting for task id from backend before sending cancel request...');
      }

      return;
    }

    options.onBack();
  });

  const startRun = (startLogMessage: string): AbortController => {
    hasRequestedCancelRef.current = false;
    waitingTaskIdForCancelRef.current = false;
    taskIdRef.current = undefined;
    setAwaitingExitConfirm(false);
    setStatus('running');
    setErrorMessage(undefined);
    appendLog(startLogMessage);

    const abortController = new AbortController();
    abortControllerRef.current = abortController;
    return abortController;
  };

  const handleTaskStart = (event: SseTaskStartEvent, onStarted?: (event: SseTaskStartEvent) => void): void => {
    if (!event?.id) {
      return;
    }

    taskIdRef.current = event.id;
    appendLog(`[INFO] ${options.taskLabelTitle} task started: ${event.name} (${event.id})`);
    onStarted?.(event);

    if (waitingTaskIdForCancelRef.current) {
      waitingTaskIdForCancelRef.current = false;
      appendLog('[INFO] Sending cancellation request to backend...');
      requestCancel(event.id);
      abortControllerRef.current?.abort();
    }
  };

  const handleProgress = (event: SseTaskStageProgress): void => {
    const level = event.isError ? 'ERROR' : 'INFO';
    setStageMessage(`${event.stage} - ${event.message}`);
    appendLog(`[${event.stage}] ${level} - ${event.message}`);
  };

  const handleError = (event: SseTaskErrorEvent): void => {
    setStatus('error');
    setErrorMessage(`${event.code}: ${event.message}`);
    setStageMessage(options.failedStageMessage);
    appendLog(`[ERROR] ${event.code}: ${event.message}`);
  };

  const completeRun = (successStageMessage: string, successLogMessage: string): void => {
    setStatus('success');
    setStageMessage(successStageMessage);
    appendLog(successLogMessage);
  };

  const handleRunFailure = (error: unknown, abortController: AbortController, abortLogMessage: string): void => {
    if (abortController.signal.aborted) {
      setStatus('error');
      setErrorMessage(undefined);
      setStageMessage(options.cancelledStageMessage);
      appendLog(abortLogMessage);
      return;
    }

    const message = error instanceof Error ? error.message : String(error);
    setStatus('error');
    setErrorMessage(message);
    setStageMessage(options.failedStageMessage);
    appendLog(`[ERROR] ${message}`);
  };

  const cleanupRun = (abortController: AbortController): void => {
    if (abortControllerRef.current === abortController) {
      abortControllerRef.current = undefined;
    }

    waitingTaskIdForCancelRef.current = false;
    abortController.abort();
  };

  return {
    stageMessage,
    status,
    errorMessage,
    awaitingExitConfirm,
    progressLogs,
    setStageMessage,
    setStatus,
    setErrorMessage,
    appendLog,
    startRun,
    handleTaskStart,
    handleProgress,
    handleError,
    completeRun,
    handleRunFailure,
    cleanupRun,
  };
}
