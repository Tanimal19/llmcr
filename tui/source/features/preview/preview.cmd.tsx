import { readFile } from 'node:fs/promises';
import { basename } from 'node:path';
import { useEffect, useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { ArgInput, LoadingSpinner } from '#components';
import { type CommandProps } from '#features/types.js';
import { type CodeReviewReport } from '#features/review/review.api.js';

type PreviewCommandProps = {
  reportPath?: string;
} & CommandProps;

const isObjectRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const resolveReviewReport = (payload: unknown): CodeReviewReport => {
  if (!isObjectRecord(payload)) {
    throw new Error('Report JSON must be an object.');
  }

  const nestedReport = payload['reviewReport'];
  const candidate = isObjectRecord(nestedReport) ? nestedReport : (payload as Partial<CodeReviewReport>);

  if (typeof candidate['prId'] !== 'number' || typeof candidate['prTitle'] !== 'string') {
    throw new Error('Invalid report JSON: expected reviewReport with prId:number and prTitle:string.');
  }

  return candidate as CodeReviewReport;
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

  const summary = reviewReport?.content ?? reviewReport?.mainReport;
  const checklistItems = reviewReport?.checklistItems ?? reviewReport?.itemAnswers ?? [];
  const goodPoints = summary?.goodPoints ?? [];
  const badPoints = summary?.badPoints ?? [];
  const implementationDetails = summary?.implementationDetails ?? [];
  const issues = summary?.issues ?? [];
  const displayPath = activePath.length > 80 ? basename(activePath) : activePath;
  const issueKeyCounts = new Map<string, number>();
  const sectionLineKeyCounts = new Map<string, number>();

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

      {!isLoading && reviewReport ? (
        <Box flexDirection="column" marginTop={1}>
          <Text color="cyan">Overview</Text>
          <Text color="white">
            PR: #{reviewReport.prId} {reviewReport.prTitle}
          </Text>
          <Text color="white">Checklist Items: {checklistItems.length}</Text>
          <Text color="white">Issues: {issues.length}</Text>

          <Box flexDirection="column" marginTop={1}>
            <Text color="cyan">Interpretation</Text>
            <Text color="white">Change Description:</Text>
            <Text color="gray">{reviewReport.interpretation?.changeDescription ?? '(none)'}</Text>
            <Text color="white">Change Motivation:</Text>
            <Text color="gray">{reviewReport.interpretation?.changeMotivation ?? '(none)'}</Text>
          </Box>

          <Box flexDirection="column" marginTop={1}>
            <Text color="cyan">Main Report</Text>
            <Text color="white">Motivation:</Text>
            <Text color="gray">{summary?.motivation ?? '(none)'}</Text>
            <Text color="white">Suggestion:</Text>
            <Text color="gray">{summary?.suggestion ?? '(none)'}</Text>
          </Box>

          <Box flexDirection="column" marginTop={1}>
            <Text color="cyan">Good Points</Text>
            {goodPoints.length === 0 ? <Text color="gray">(none)</Text> : null}
            {goodPoints.map(point => {
              const keyCount = (sectionLineKeyCounts.get(`good-${point}`) ?? 0) + 1;
              sectionLineKeyCounts.set(`good-${point}`, keyCount);
              return (
                <Text key={`good-${point}-${keyCount}`} color="gray">
                  - {point}
                </Text>
              );
            })}
          </Box>

          <Box flexDirection="column" marginTop={1}>
            <Text color="cyan">Bad Points</Text>
            {badPoints.length === 0 ? <Text color="gray">(none)</Text> : null}
            {badPoints.map(point => {
              const keyCount = (sectionLineKeyCounts.get(`bad-${point}`) ?? 0) + 1;
              sectionLineKeyCounts.set(`bad-${point}`, keyCount);
              return (
                <Text key={`bad-${point}-${keyCount}`} color="gray">
                  - {point}
                </Text>
              );
            })}
          </Box>

          <Box flexDirection="column" marginTop={1}>
            <Text color="cyan">Implementation Details</Text>
            {implementationDetails.length === 0 ? <Text color="gray">(none)</Text> : null}
            {implementationDetails.map(detail => {
              const detailKeyBase = `${detail.filename}`;
              const detailKeyCount = (sectionLineKeyCounts.get(detailKeyBase) ?? 0) + 1;
              sectionLineKeyCounts.set(detailKeyBase, detailKeyCount);
              const detailKey = `${detailKeyBase}-${detailKeyCount}`;

              return (
                <Box key={detailKey} flexDirection="column" marginTop={1}>
                  <Text color="white">File: {detail.filename}</Text>
                  {detail.details.length === 0 ? <Text color="gray"> - (none)</Text> : null}
                  {detail.details.map(detailLine => {
                    const lineKeyBase = `${detail.filename}-${detailLine}`;
                    const lineKeyCount = (sectionLineKeyCounts.get(lineKeyBase) ?? 0) + 1;
                    sectionLineKeyCounts.set(lineKeyBase, lineKeyCount);
                    return (
                      <Text key={`${lineKeyBase}-${lineKeyCount}`} color="gray">
                        - {detailLine}
                      </Text>
                    );
                  })}
                </Box>
              );
            })}
          </Box>

          {issues.length > 0 ? (
            <Box flexDirection="column" marginTop={1}>
              <Text color="cyan">Issues</Text>
              {issues.map((issue, index) => {
                const locationStr = issue.location ? ` @ ${issue.location}` : '';
                const issueKeyBase = `${issue.type}-${issue.title}-${issue.location}`;
                const issueKeyCount = (issueKeyCounts.get(issueKeyBase) ?? 0) + 1;
                issueKeyCounts.set(issueKeyBase, issueKeyCount);
                const issueKey = `${issueKeyBase}-${issueKeyCount}`;

                return (
                  <Box key={issueKey} flexDirection="column" marginTop={1}>
                    <Text color="white">{`${index + 1}. [${issue.type}] ${issue.title}${locationStr}`}</Text>
                    <Text color="gray">Detail: {issue.detail || '(none)'}</Text>
                  </Box>
                );
              })}
            </Box>
          ) : (
            <Box flexDirection="column" marginTop={1}>
              <Text color="cyan">Issues</Text>
              <Text color="gray">(none)</Text>
            </Box>
          )}

          <Box flexDirection="column" marginTop={1}>
            <Text color="cyan">Checklist</Text>
            {checklistItems.length === 0 ? <Text color="gray">(none)</Text> : null}
            {checklistItems.map(item => {
              const itemTitle = item.title ?? item.checklistItemTitle;
              const itemKeyBase = `${item.checklistItemTitle}-${item.answer.finalAnswer}`;
              const itemKeyCount = (sectionLineKeyCounts.get(itemKeyBase) ?? 0) + 1;
              sectionLineKeyCounts.set(itemKeyBase, itemKeyCount);
              const itemKey = `${itemKeyBase}-${itemKeyCount}`;

              return (
                <Box key={itemKey} flexDirection="column" marginTop={1}>
                  <Text color="white">Item: {itemTitle}</Text>
                  <Text color="gray">Final Answer: {item.answer.finalAnswer || '(none)'}</Text>
                  <Text color="gray">Analysis: {item.answer.analysis || '(none)'}</Text>
                  <Text color="white">Evidence:</Text>
                  {item.answer.evidence.length === 0 ? <Text color="gray"> - (none)</Text> : null}
                  {item.answer.evidence.map(evidence => {
                    const evidenceKeyBase = `${itemTitle}-${evidence.file}-${evidence.lines}-${evidence.reason}`;
                    const evidenceKeyCount = (sectionLineKeyCounts.get(evidenceKeyBase) ?? 0) + 1;
                    sectionLineKeyCounts.set(evidenceKeyBase, evidenceKeyCount);
                    const evidenceKey = `${evidenceKeyBase}-${evidenceKeyCount}`;

                    return (
                      <Box key={evidenceKey} flexDirection="column" marginTop={0}>
                        <Text color="gray">- File: {evidence.file || '(none)'}</Text>
                        <Text color="gray"> Lines: {evidence.lines || '(none)'}</Text>
                        <Text color="gray"> Reason: {evidence.reason || '(none)'}</Text>
                      </Box>
                    );
                  })}
                </Box>
              );
            })}
          </Box>
        </Box>
      ) : null}
    </Box>
  );
};
