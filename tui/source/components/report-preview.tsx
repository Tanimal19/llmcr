import { type PropsWithChildren } from 'react';
import { Box, Text } from 'ink';
import {
  type CodeReviewImplementationDetails,
  type CodeReviewIssue,
  type CodeReviewInterpretation,
  type CodeReviewItemAnswer,
  type CodeReviewReport,
  type CodeReviewSummary,
} from '#features/review/review.api.js';

type ReviewReportPreviewProps = {
  reviewReport: CodeReviewReport;
  reportPath?: string;
  maxIssuePreviewCount?: number;
};

type NormalizedReportData = {
  summary?: CodeReviewSummary;
  interpretation?: CodeReviewInterpretation;
  checklistItems: CodeReviewItemAnswer[];
  goodPoints: string[];
  badPoints: string[];
  implementationDetails: CodeReviewImplementationDetails[];
  issues: CodeReviewIssue[];
  staticAnalysisResults?: string;
};

const normalizeReport = (reviewReport: CodeReviewReport): NormalizedReportData => {
  const summary = reviewReport.content ?? undefined;
  const interpretation = reviewReport.interpretation ?? undefined;

  return {
    summary,
    interpretation,
    checklistItems: reviewReport.checklistItems ?? [],
    goodPoints: summary?.goodPoints ?? [],
    badPoints: summary?.badPoints ?? [],
    implementationDetails: summary?.implementationDetails ?? [],
    issues: summary?.issues ?? [],
    staticAnalysisResults: reviewReport.staticAnalysisResults,
  };
};

const Empty = () => <Text color="gray">(none)</Text>;

const Section = ({ title, children }: PropsWithChildren<{ title: string }>) => (
  <Box flexDirection="column" marginTop={1}>
    <Text color="cyan">{title}</Text>
    {children}
  </Box>
);

const StringListSection = ({ title, items }: { title: string; items: string[] }) => (
  <Section title={title}>
    {items.length === 0 ? (
      <Empty />
    ) : (
      items.map((item, index) => (
        <Text key={index} color="gray">
          - {item}
        </Text>
      ))
    )}
  </Section>
);

const ReviewHeader = ({
  reviewReport,
  reportPath,
  checklistCount,
  issueCount,
}: {
  reviewReport: CodeReviewReport;
  reportPath?: string;
  checklistCount: number;
  issueCount: number;
}) => (
  <>
    <Text color="white">
      PR: #{reviewReport.prId} {reviewReport.prTitle}
    </Text>
    {reportPath ? <Text color="gray">Report: {reportPath}</Text> : null}
    <Text color="white">Checklist Items: {checklistCount}</Text>
    <Text color="white">Issues: {issueCount}</Text>
  </>
);

const ImplementationDetailsSection = ({ details }: { details: CodeReviewImplementationDetails[] }) => (
  <Section title="Implementation Details">
    {details.length === 0 ? (
      <Empty />
    ) : (
      details.map((detail, detailIndex) => (
        <Box key={`${detail.filename}-${detailIndex}`} flexDirection="column" marginTop={1}>
          <Text color="white">File: {detail.filename}</Text>
          {detail.details.length === 0 ? (
            <Text color="gray"> - (none)</Text>
          ) : (
            detail.details.map((detailLine, lineIndex) => (
              <Text key={lineIndex} color="gray">
                - {detailLine}
              </Text>
            ))
          )}
        </Box>
      ))
    )}
  </Section>
);

const IssuesSection = ({
  issues,
  maxIssuePreviewCount,
}: {
  issues: CodeReviewIssue[];
  maxIssuePreviewCount?: number;
}) => {
  const limitedIssues =
    maxIssuePreviewCount && maxIssuePreviewCount > 0 ? issues.slice(0, maxIssuePreviewCount) : issues;
  const hiddenIssueCount = issues.length - limitedIssues.length;

  return (
    <Section title="Issues">
      {limitedIssues.length === 0 ? (
        <Empty />
      ) : (
        limitedIssues.map((issue, index) => {
          const locationStr = issue.location ? ` @ ${issue.location}` : '';

          return (
            <Box key={`${issue.type}-${issue.title}-${index}`} flexDirection="column" marginTop={1}>
              <Text color="white">{`${index + 1}. [${issue.type}] ${issue.title}${locationStr}`}</Text>
              <Text color="gray">Detail: {issue.detail || '(none)'}</Text>
            </Box>
          );
        })
      )}
      {hiddenIssueCount > 0 ? <Text color="gray">... and {hiddenIssueCount} more issue(s)</Text> : null}
    </Section>
  );
};

const InterpretationSection = ({ interpretation }: { interpretation?: CodeReviewInterpretation }) => (
  <Section title="Interpretation">
    <Text color="white">Change Description:</Text>
    <Text color="gray">{interpretation?.changeDescription ?? '(none)'}</Text>
    <Text color="white">Change Motivation:</Text>
    <Text color="gray">{interpretation?.changeMotivation ?? '(none)'}</Text>
  </Section>
);

const StaticAnalysisSection = ({ staticAnalysisResults }: { staticAnalysisResults?: string }) => (
  <Section title="Static Analysis Results">
    <Text color="gray">{staticAnalysisResults ?? '(none)'}</Text>
  </Section>
);

const ChecklistSection = ({ checklistItems }: { checklistItems: CodeReviewItemAnswer[] }) => (
  <Section title="Checklist">
    {checklistItems.length === 0 ? (
      <Empty />
    ) : (
      checklistItems.map((item, itemIndex) => {
        const itemTitle = item.title;

        return (
          <Box key={`${itemTitle}-${itemIndex}`} flexDirection="column" marginTop={1}>
            <Text color="white">Item: {itemTitle}</Text>
            <Text color="gray">Final Answer: {item.answer.finalAnswer || '(none)'}</Text>
            <Text color="gray">Analysis: {item.answer.analysis || '(none)'}</Text>
            <Text color="white">Evidence:</Text>
            {item.answer.evidence.length === 0 ? (
              <Text color="gray"> - (none)</Text>
            ) : (
              item.answer.evidence.map((evidence, evidenceIndex) => (
                <Box key={`${itemTitle}-${evidenceIndex}`} flexDirection="column">
                  <Text color="gray">- File: {evidence.file || '(none)'}</Text>
                  <Text color="gray"> Lines: {evidence.lines || '(none)'}</Text>
                  <Text color="gray"> Reason: {evidence.reason || '(none)'}</Text>
                </Box>
              ))
            )}
          </Box>
        );
      })
    )}
  </Section>
);

const FullPreview = ({ reviewReport, reportPath, maxIssuePreviewCount }: ReviewReportPreviewProps) => {
  const {
    summary,
    interpretation,
    checklistItems,
    goodPoints,
    badPoints,
    implementationDetails,
    issues,
    staticAnalysisResults,
  } = normalizeReport(reviewReport);

  return (
    <Box flexDirection="column" marginTop={1}>
      <Text color="cyan">Overview</Text>
      <ReviewHeader
        reviewReport={reviewReport}
        reportPath={reportPath}
        checklistCount={checklistItems.length}
        issueCount={issues.length}
      />

      <Section title="Motivation">
        <Text color="gray">{summary?.motivation ?? '(none)'}</Text>
      </Section>

      <Section title="Suggestion">
        <Text color="gray">{summary?.suggestion ?? '(none)'}</Text>
      </Section>

      <StringListSection title="Good Points" items={goodPoints} />
      <StringListSection title="Bad Points" items={badPoints} />
      <InterpretationSection interpretation={interpretation} />
      <ImplementationDetailsSection details={implementationDetails} />
      <IssuesSection issues={issues} maxIssuePreviewCount={maxIssuePreviewCount} />
      <ChecklistSection checklistItems={checklistItems} />
      <StaticAnalysisSection staticAnalysisResults={staticAnalysisResults} />
    </Box>
  );
};

export const ReviewReportPreview = (props: ReviewReportPreviewProps) => {
  return <FullPreview {...props} />;
};
