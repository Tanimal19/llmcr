import { useState, useEffect } from 'react';
import { Box, Text } from 'ink';
import { cancelReviewTask, type CodeReviewOutput, reviewWithProgress } from './review.api.js';
import { useSseTaskLifecycle } from '#hooks';
import { type CommandProps } from '#features/types.js';
import { ArgInput, LoadingSpinner } from '#components';
import { ReviewReportPreview } from '#components/report-preview.js';

type ReviewCommandProps = {
  diffPath?: string;
  jsonlIndex?: number;
  useMock?: boolean;
} & CommandProps;

type ReviewArgs = {
  inputFilePath: string;
  jsonlIndex?: number;
  useMock: boolean;
};

const JSONL_INPUT_WITH_INDEX_PATTERN = /^(?<path>.+)::(?<index>\d+)$/v;

type ParsedReviewInput = {
  inputFilePath: string;
  jsonlIndex?: number;
  useMock: boolean;
  errorMessage?: string;
};

const parseReviewInput = (rawValue: string): ParsedReviewInput => {
  const value = rawValue.trim();
  if (value.length === 0) {
    return {
      inputFilePath: '',
      useMock: true,
    };
  }

  const parsed = JSONL_INPUT_WITH_INDEX_PATTERN.exec(value);
  if (!parsed?.groups) {
    return {
      inputFilePath: value,
      useMock: false,
    };
  }

  const pathValue = parsed.groups['path'];
  const indexToken = parsed.groups['index'];
  const inputFilePath = (pathValue ?? '').trim();
  const indexValue = Number(indexToken);
  if (inputFilePath.length === 0 || !Number.isInteger(indexValue) || indexValue < 0) {
    return {
      inputFilePath: value,
      useMock: false,
      errorMessage: 'Invalid jsonl index format. Use: <path>.jsonl::0',
    };
  }

  return {
    inputFilePath,
    jsonlIndex: indexValue,
    useMock: false,
  };
};

const getInitialArgs = (
  diffPath: string | undefined,
  jsonlIndex: number | undefined,
  useMock: boolean,
): ReviewArgs | undefined => {
  if (diffPath === undefined) {
    return undefined;
  }

  return {
    inputFilePath: diffPath,
    jsonlIndex,
    useMock,
  };
};

type ValidationLifecycle = {
  setErrorStatus: () => void;
  setErrorMessage: (errorMessage: string | undefined) => void;
  setStageMessage: (stageMessage: string) => void;
  appendLog: (entry: string) => void;
};

const validateArgsAndPrepare = (args: ReviewArgs, lifecycle: ValidationLifecycle): boolean => {
  if ((!args.inputFilePath || args.inputFilePath.trim().length === 0) && !args.useMock) {
    lifecycle.setErrorStatus();
    lifecycle.setErrorMessage('Input file path is required.');
    lifecycle.setStageMessage('Review did not start');
    lifecycle.appendLog('[ERROR] Review did not start: Input file path is required.');
    return false;
  }

  if (args.useMock) {
    lifecycle.setStageMessage('Using mock review data...');
    lifecycle.appendLog('[INFO] Using mock review data');
  }

  return true;
};

export const ReviewCommand = ({ onBack, diffPath, jsonlIndex, useMock = false }: ReviewCommandProps) => {
  const [args, setArgs] = useState<ReviewArgs | undefined>(() => getInitialArgs(diffPath, jsonlIndex, useMock));

  const [reviewResult, setReviewResult] = useState<CodeReviewOutput | undefined>(undefined);

  const {
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
  } = useSseTaskLifecycle({
    initialStageMessage: 'Waiting to start review...',
    cancellingStageMessage: 'Cancelling review...',
    cancelledStageMessage: 'Review cancelled by user',
    failedStageMessage: 'Review failed',
    taskLabelLower: 'review',
    taskLabelTitle: 'Review',
    onBack,
    cancelTask: cancelReviewTask,
  });

  useEffect(() => {
    if (!args) {
      return;
    }

    if (
      !validateArgsAndPrepare(args, {
        setErrorStatus() {
          setStatus('error');
        },
        setErrorMessage,
        setStageMessage,
        appendLog,
      })
    ) {
      return;
    }

    const { inputFilePath: activePath, jsonlIndex: activeJsonlIndex, useMock: activeMock } = args;

    const abortController = startRun('[INFO] Review started');

    reviewWithProgress(
      {
        inputFilePath: activePath ?? '',
        jsonlIndex: activeJsonlIndex,
        useMockData: activeMock,
      },
      {
        onStart: handleTaskStart,
        onProgress: handleProgress,
        onError: handleError,
        useMock: activeMock,
        onResult(result) {
          setReviewResult(result);
          completeRun('Review completed successfully', '[DONE] Review completed successfully');
        },
        signal: abortController.signal,
      },
    ).catch((error: unknown) => {
      handleRunFailure(error, abortController, '[INFO] Review stream aborted');
    });

    return () => {
      cleanupRun(abortController);
    };
  }, [args]);

  if (!args) {
    return (
      <ArgInput
        title="Please enter PR input path (JSON or JSONL)"
        placeholder="./example.json or ./example.jsonl::0 (leave empty to use mock data)"
        usePlaceholderOnEmpty={false}
        onCancel={onBack}
        onSubmit={value => {
          const parsedInput = parseReviewInput(value);
          if (parsedInput.errorMessage) {
            setStatus('error');
            setErrorMessage(parsedInput.errorMessage);
            setStageMessage('Review did not start');
            appendLog(`[ERROR] Review did not start: ${parsedInput.errorMessage}`);
            return;
          }

          setErrorMessage(undefined);
          setArgs(parsedInput);
        }}
      />
    );
  }

  const { inputFilePath: activePath, jsonlIndex: activeJsonlIndex, useMock: activeMock } = args;
  const logCounts = new Map<string, number>();

  return (
    <Box flexDirection="column" padding={1}>
      {activePath && <Text color="yellow">Performing code review on: {activePath}</Text>}
      {activeJsonlIndex !== undefined && <Text color="yellow">Using JSONL index: {activeJsonlIndex}</Text>}
      {activeMock && <Text color="yellow">Performing code review using mock data</Text>}
      <Text color="gray">(Press esc to cancel)</Text>

      {status === 'running' ? (
        <LoadingSpinner message={stageMessage} color="white" />
      ) : (
        <Text color={status === 'error' ? 'red' : 'white'}>{stageMessage}</Text>
      )}

      <Box flexDirection="column" marginTop={1}>
        <Text color="cyan">Progress Log:</Text>
        {progressLogs.length === 0 && <Text color="gray">(no events yet)</Text>}
        {progressLogs.map(log => {
          // 計算當前字串在此輪渲染是第幾次出現，確保 key 絕對唯一且不依賴 Array index
          const currentCount = (logCounts.get(log) ?? 0) + 1;
          logCounts.set(log, currentCount);
          return (
            <Text key={`${log}-${currentCount}`} color="gray">
              {log}
            </Text>
          );
        })}
      </Box>

      {awaitingExitConfirm && <Text color="yellow">Cancellation requested. Press ESC again to return.</Text>}
      {status === 'success' && <Text color="green">Review done. Press ESC to return.</Text>}
      {status === 'error' && errorMessage && <Text color="red">Error: {errorMessage}</Text>}

      {status === 'success' && reviewResult && (
        <ReviewReportPreview reviewReport={reviewResult.reviewReport} reportPath={reviewResult.reportPath} />
      )}
    </Box>
  );
};
