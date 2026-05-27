import { readFile } from 'node:fs/promises';
import { basename } from 'node:path';
import { useEffect, useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { ArgInput, LoadingSpinner } from '#components';
import { ReviewReportPreview } from '#components/report-preview.js';
import { type CommandProps } from '#features/types.js';
import { type CodeReviewReport, type CodeReviewSummary } from '#features/review/review.api.js';

type PreviewCommandProps = {
  reportPath?: string;
} & CommandProps;

const isObjectRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const isString = (value: unknown): value is string => typeof value === 'string';

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every(item => isString(item));

const isIssue = (value: unknown): value is CodeReviewSummary['issues'][number] => {
  if (!isObjectRecord(value)) {
    return false;
  }

  return (
    isString(value['type']) && isString(value['title']) && isString(value['location']) && isString(value['detail'])
  );
};

const isImplementationDetails = (value: unknown): value is CodeReviewSummary['implementationDetails'][number] => {
  if (!isObjectRecord(value)) {
    return false;
  }

  return isString(value['filename']) && isStringArray(value['details']);
};

const isSummary = (value: unknown): value is CodeReviewSummary => {
  if (!isObjectRecord(value)) {
    return false;
  }

  return (
    isString(value['motivation']) &&
    isStringArray(value['goodPoints']) &&
    isStringArray(value['badPoints']) &&
    isString(value['suggestion']) &&
    Array.isArray(value['implementationDetails']) &&
    value['implementationDetails'].every(item => isImplementationDetails(item)) &&
    Array.isArray(value['issues']) &&
    value['issues'].every(item => isIssue(item))
  );
};

const isItemAnswer = (value: unknown): value is NonNullable<CodeReviewReport['checklistItems']>[number] => {
  if (!isObjectRecord(value) || !isString(value['title'])) {
    return false;
  }

  const { answer } = value;
  if (!isObjectRecord(answer)) {
    return false;
  }

  if (!isString(answer['finalAnswer']) || !isString(answer['analysis']) || !Array.isArray(answer['evidence'])) {
    return false;
  }

  return answer['evidence'].every(evidence => {
    if (!isObjectRecord(evidence)) {
      return false;
    }

    return isString(evidence['file']) && isString(evidence['lines']) && isString(evidence['reason']);
  });
};

const asChecklistItems = (value: unknown): CodeReviewReport['checklistItems'] => {
  if (!Array.isArray(value) || !value.every(item => isItemAnswer(item))) {
    throw new TypeError('Invalid report JSON: checklistItems must be an array of checklist items.');
  }

  return value;
};

const asSummary = (value: unknown): CodeReviewSummary => {
  if (!isSummary(value)) {
    throw new TypeError('Invalid report JSON: content must be a valid summary object.');
  }

  return value;
};

const asInterpretation = (value: unknown): CodeReviewReport['interpretation'] => {
  if (!isObjectRecord(value)) {
    throw new TypeError('Invalid report JSON: interpretation must be an object.');
  }

  if (!isString(value['changeDescription']) || !isString(value['changeMotivation'])) {
    throw new TypeError(
      'Invalid report JSON: interpretation.changeDescription and interpretation.changeMotivation must be strings.',
    );
  }

  return {
    changeDescription: value['changeDescription'],
    changeMotivation: value['changeMotivation'],
  };
};

const resolveReviewReport = (payload: unknown): CodeReviewReport => {
  if (!isObjectRecord(payload)) {
    throw new TypeError('Report JSON must be an object.');
  }

  const nestedReport = payload['reviewReport'];
  const candidate = isObjectRecord(nestedReport) ? nestedReport : payload;

  if (typeof candidate['prId'] !== 'number' || typeof candidate['prTitle'] !== 'string') {
    throw new TypeError('Invalid report JSON: expected reviewReport with prId:number and prTitle:string.');
  }

  return {
    prId: candidate['prId'],
    prTitle: candidate['prTitle'],
    interpretation: asInterpretation(candidate['interpretation']),
    content: asSummary(candidate['content']),
    checklistItems: asChecklistItems(candidate['checklistItems']),
  };
};

export const PreviewCommand = ({ onBack, reportPath }: PreviewCommandProps) => {
  const [activePath, setActivePath] = useState<string | undefined>(reportPath);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
  const [reviewReport, setReviewReport] = useState<CodeReviewReport | undefined>(undefined);

  useInput((_, key) => {
    if (key.escape && !isLoading) {
      onBack();
    }
  });

  useEffect(() => {
    if (!activePath) {
      return;
    }

    let alive = true;

    (async () => {
      setIsLoading(true);
      setErrorMessage(undefined);
      setReviewReport(undefined);

      try {
        const raw = await readFile(activePath, 'utf8');
        const parsed = JSON.parse(raw) as unknown;
        const resolved = resolveReviewReport(parsed);

        if (!alive) {
          return;
        }

        setReviewReport(resolved);
      } catch (error) {
        if (!alive) {
          return;
        }

        setErrorMessage(error instanceof Error ? error.message : String(error));
      } finally {
        if (alive) {
          setIsLoading(false);
        }
      }
    })();

    return () => {
      alive = false;
    };
  }, [activePath]);

  if (!activePath) {
    return (
      <ArgInput
        title="Please enter the path to review report JSON"
        placeholder="./review-report.json"
        usePlaceholderOnEmpty={false}
        onCancel={onBack}
        onSubmit={value => {
          setActivePath(value);
        }}
      />
    );
  }

  const displayPath = activePath.length > 80 ? basename(activePath) : activePath;

  return (
    <Box flexDirection="column" padding={1}>
      <Text color="yellow">Previewing review report: {displayPath}</Text>
      <Text color="gray">(Press esc to return)</Text>

      {isLoading ? (
        <LoadingSpinner message="Loading report..." color="white" />
      ) : (
        <Text color={errorMessage ? 'red' : 'green'}>{errorMessage ? 'Failed to load report' : 'Report loaded'}</Text>
      )}

      {!isLoading && errorMessage ? <Text color="red">Error: {errorMessage}</Text> : null}

      {!isLoading && reviewReport ? <ReviewReportPreview reviewReport={reviewReport} /> : null}
    </Box>
  );
};
