import { type PropsWithChildren } from 'react';
import { Box, Text } from 'ink';
import {
  type CodeReviewImplementationDetails,
  type CodeReviewIssue,
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
  checklistItems: CodeReviewItemAnswer[];
  goodPoints: string[];
  badPoints: string[];
  implementationDetails: CodeReviewImplementationDetails[];
  issues: CodeReviewIssue[];
};

const normalizeReport = (reviewReport: CodeReviewReport): NormalizedReportData => {
  const summary = reviewReport.content ?? undefined;

  return {
    summary,
    checklistItems: reviewReport.checklistItems ?? [],
    goodPoints: summary?.goodPoints ?? [],
    badPoints: summary?.badPoints ?? [],
    implementationDetails: summary?.implementationDetails ?? [],
    issues: summary?.issues ?? [],
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
  checklistCount,
  issueCount,
}: {
  reviewReport: CodeReviewReport;
  checklistCount: number;
  issueCount: number;
}) => (
  <>
    <Text color="white">
      PR: #{reviewReport.prId} {reviewReport.prTitle}
    </Text>
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

const IssuesSection = ({ issues }: { issues: CodeReviewIssue[] }) => (
  <Section title="Issues">
    {issues.length === 0 ? (
      <Empty />
    ) : (
      issues.map((issue, index) => {
        const locationStr = issue.location ? ` @ ${issue.location}` : '';

        return (
          <Box key={`${issue.type}-${issue.title}-${index}`} flexDirection="column" marginTop={1}>
            <Text color="white">{`${index + 1}. [${issue.type}] ${issue.title}${locationStr}`}</Text>
            <Text color="gray">Detail: {issue.detail || '(none)'}</Text>
          </Box>
        );
      })
    )}
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

const FullPreview = ({ reviewReport }: ReviewReportPreviewProps) => {
  const { summary, checklistItems, goodPoints, badPoints, implementationDetails, issues } =
    normalizeReport(reviewReport);

  return (
    <Box flexDirection="column" marginTop={1}>
      <Text color="cyan">Overview</Text>
      <ReviewHeader reviewReport={reviewReport} checklistCount={checklistItems.length} issueCount={issues.length} />

      <Section title="Motivation">
        <Text color="gray">{summary?.motivation ?? '(none)'}</Text>
      </Section>

      <Section title="Suggestion">
        <Text color="gray">{summary?.suggestion ?? '(none)'}</Text>
      </Section>

      <StringListSection title="Good Points" items={goodPoints} />
      <StringListSection title="Bad Points" items={badPoints} />
      <ImplementationDetailsSection details={implementationDetails} />
      <IssuesSection issues={issues} />
      <ChecklistSection checklistItems={checklistItems} />
    </Box>
  );
};

export const ReviewReportPreview = (props: ReviewReportPreviewProps) => {
  return <FullPreview {...props} />;
};
