package com.llmcr.review;

import com.llmcr.api.APIServiceException;
import com.llmcr.api.SseTaskManager.TaskProgressEvent;
import com.llmcr.api.SseTaskObject;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.review.PullRequestParser.PullRequestData;
import com.llmcr.review.agent.ComputationAgent;
import com.llmcr.review.agent.InterpretationAgent;
import com.llmcr.review.agent.PlanningAgent;
import com.llmcr.review.agent.SummaryAgent;
import com.llmcr.review.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.review.agent.ComputationAgent.ComputationAgentOutput;
import com.llmcr.review.agent.ComputationAgent.EvidenceItem;
import com.llmcr.review.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.review.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.review.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.review.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.review.agent.SummaryAgent.Issue;
import com.llmcr.review.agent.SummaryAgent.ItemAnswer;
import com.llmcr.review.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.review.agent.SummaryAgent.SummaryAgentOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CodeReviewService
        implements SseTaskObject<CodeReviewService.CodeReviewInput, CodeReviewService.CodeReviewOutput> {

    private static final Logger logger = LoggerFactory.getLogger(CodeReviewService.class);
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final InterpretationAgent interpretationAgent;
    private final PlanningAgent planningAgent;
    private final ComputationAgent computationAgent;
    private final SummaryAgent summaryAgent;

    private String outputDir;

    public CodeReviewService(
            ApplicationProperties applicationProperties,
            InterpretationAgent interpretationAgent,
            PlanningAgent planningAgent,
            ComputationAgent computationAgent,
            SummaryAgent summaryAgent) {
        this.outputDir = applicationProperties.getLogging().getReviewOutputDir();
        this.interpretationAgent = interpretationAgent;
        this.planningAgent = planningAgent;
        this.computationAgent = computationAgent;
        this.summaryAgent = summaryAgent;
    }

    public record CodeReviewInput(String jsonFilePath, boolean useMockData) {
    }

    public record CodeReviewOutput(CodeReviewReport reviewReport, Path reportPath) {
    }

    public record CodeChange(String filePath, String diffContent) {
    }

    public record CodeReviewReport(
            int prId,
            String prTitle,
            SummaryAgentOutput mainReport,
            InterpretationAgentOutput interpretation,
            List<ItemAnswer> itemAnswers) {
        public String toString() {
            return toMarkdown(this);
        }

        private static String toMarkdown(CodeReviewReport report) {
            StringBuilder sb = new StringBuilder();

            // Summary Report section
            sb.append("# Code Review Report\n\n");
            sb.append("**PR ID:** ").append(report.prId()).append("\n\n");
            sb.append("**PR Title:** ").append(report.prTitle()).append("\n\n");

            if (report != null && report.mainReport() != null) {
                sb.append("## Motivation\n\n");
                if (report.mainReport().motivation() != null && !report.mainReport().motivation().isBlank()) {
                    sb.append(report.mainReport().motivation()).append("\n\n");
                } else {
                    sb.append("_No motivation provided._\n\n");
                }

                sb.append("## Good Points\n\n");
                if (report.mainReport().goodPoints() != null && !report.mainReport().goodPoints().isEmpty()) {
                    for (String point : report.mainReport().goodPoints()) {
                        sb.append("- ").append(point).append("\n");
                    }
                    sb.append("\n");
                } else {
                    sb.append("_No good points provided._\n\n");
                }

                sb.append("## Bad Points\n\n");
                if (report.mainReport().badPoints() != null && !report.mainReport().badPoints().isEmpty()) {
                    for (String point : report.mainReport().badPoints()) {
                        sb.append("- ").append(point).append("\n");
                    }
                    sb.append("\n");
                } else {
                    sb.append("_No bad points provided._\n\n");
                }

                sb.append("## Suggestion\n\n");
                if (report.mainReport().suggestion() != null && !report.mainReport().suggestion().isBlank()) {
                    sb.append(report.mainReport().suggestion()).append("\n\n");
                } else {
                    sb.append("_No suggestion provided._\n\n");
                }

                sb.append("## Implementation Details\n\n");
                if (report.mainReport().implementationDetails() != null &&
                        !report.mainReport().implementationDetails().isEmpty()) {
                    for (var detailsByFile : report.mainReport().implementationDetails()) {
                        String filename = detailsByFile.filename() != null
                                ? detailsByFile.filename()
                                : "(unknown file)";
                        sb.append("#### ").append(filename).append("\n\n");
                        if (detailsByFile.details() != null && !detailsByFile.details().isEmpty()) {
                            for (String detail : detailsByFile.details()) {
                                sb.append("- ").append(detail).append("\n");
                            }
                        } else {
                            sb.append("- _No details provided._\n");
                        }
                        sb.append("\n");
                    }
                }

                sb.append("## Issues\n\n");
                if (report.mainReport().issues() != null && !report.mainReport().issues().isEmpty()) {
                    sb.append("| Type | Title | Location | Detail |\n");
                    sb.append("|------|-------|----------|--------|\n");
                    for (Issue issue : report.mainReport().issues()) {
                        String location = issue.location() != null ? issue.location() : "";
                        String type = issue.type() != null ? issue.type() : "";
                        sb
                                .append("| ")
                                .append(type)
                                .append(" | ")
                                .append(issue.title())
                                .append(" | ")
                                .append(location)
                                .append(" | ")
                                .append(issue.detail())
                                .append(" |\n");
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("_No summary available._\n\n");
            }

            // Appendix with interpretation results
            sb.append("\n\n");
            sb.append("# Appendix: Original Interpretation Results\n\n");
            if (report != null && report.interpretation() != null) {
                InterpretationAgentOutput interpretation = report.interpretation();
                if (interpretation.changeDescription() != null) {
                    sb.append("### Change Description\n\n");
                    sb.append(interpretation.changeDescription()).append("\n\n");
                }
                if (interpretation.changeMotivation() != null) {
                    sb.append("### Change Motivation\n\n");
                    sb.append(interpretation.changeMotivation()).append("\n\n");
                }
            } else {
                sb.append("_No interpretation results available._\n\n");
            }

            // Appendix with detailed checklist item answers
            sb.append("\n\n");
            sb.append("# Appendix: Detailed Checklist Item Answers\n\n");
            if (report != null && report.itemAnswers() != null && !report.itemAnswers().isEmpty()) {
                for (ItemAnswer itemAnswer : report.itemAnswers()) {
                    sb.append("### ").append(itemAnswer.checklistItemTitle()).append("\n\n");
                    sb.append(itemAnswer.answer().finalAnswer()).append("\n");
                    sb.append(itemAnswer.answer().analysis()).append("\n");
                    for (EvidenceItem evdience : itemAnswer.answer().evidence()) {
                        sb
                                .append("- ")
                                .append(evdience.file())
                                .append(":::")
                                .append(evdience.lines())
                                .append(":::")
                                .append(evdience.reason())
                                .append("\n");
                    }
                }
            } else {
                sb.append("_No checklist item answers available._\n\n");
            }

            return sb.toString();
        }
    }

    public String getTaskName() {
        return "code_review";
    }

    /**
     * Regular entry point for code review without SSE.
     */
    public CodeReviewOutput review(CodeReviewInput input) {
        return execute(input, null, () -> false);
    }

    @Override
    public CodeReviewOutput execute(
            CodeReviewInput input,
            Consumer<TaskProgressEvent> progressListener,
            BooleanSupplier cancellationRequested) {
        try {
            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "PIPELINE", "Review pipeline started");

            String jsonFilePath = input.useMockData()
                    ? MockReviewData.MOCK_PULL_REQUEST_JSON_PATH
                    : input.jsonFilePath();

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "PIPELINE", "Parsing pull request data");
            PullRequestData prData;
            try {
                prData = PullRequestParser.parseJsonFile(jsonFilePath);
            } catch (Exception ex) {
                throw new APIServiceException(
                        APIServiceException.ErrorCode.REVIEW_PARSE_FAILED,
                        "Failed to parse pull request data: " + jsonFilePath,
                        ex);
            }
            emitProgress(
                    progressListener,
                    "PIPELINE",
                    "Starting review for PR #" + prData.prId() + ": " + prData.title());

            List<CodeChange> codeChanges = prData
                    .changedFiles()
                    .stream()
                    .map(file -> new CodeChange(file.path(), file.patch()))
                    .toList();

            // TODO: integrate static analysis tool and populate codeAnalysis
            String codeAnalysis = null;

            InterpretationAgentOutput interpretation;
            PlanningAgentOutput planning;
            if (!input.useMockData()) {
                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, "INTERPRETATION", "Start interpretation stage");
                try {
                    interpretation = interpretationAgent.execute(new InterpretationAgentInput(codeChanges));
                } catch (Exception ex) {
                    throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_INTERPRETATION_FAILED, ex);
                }
                emitProgress(progressListener, "INTERPRETATION", "Interpretation stage completed");

                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, "PLANNING", "Start planning stage");
                try {
                    planning = planningAgent.execute(new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
                } catch (Exception ex) {
                    throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_PLANNING_FAILED, ex);
                }
                emitProgress(progressListener, "PLANNING", "Planning stage completed");
            } else {
                throwIfCancelled(cancellationRequested);
                interpretation = MockReviewData.MOCK_INTERPRETATION;
                planning = MockReviewData.MOCK_PLANNING;
                emitProgress(progressListener, "INTERPRETATION", "Using mock interpretation");
                emitProgress(progressListener, "PLANNING", "Using mock planning");
            }

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "COMPUTATION", "Running checklist computations");

            List<ItemAnswer> itemAnswers = new ArrayList<>();
            int totalItems = planning.checklistItems().size();
            int itemIndex = 0;
            for (String item : planning.checklistItems()) {
                throwIfCancelled(cancellationRequested);
                emitProgress(
                        progressListener,
                        "COMPUTATION",
                        "Running checklist item " + itemIndex + "/" + totalItems + ": " + item);
                itemIndex++;

                try {
                    ComputationAgentOutput answer = computationAgent.execute(
                            new ComputationAgentInput(codeChanges, item));
                    itemAnswers.add(new ItemAnswer(item, answer));
                } catch (Exception ex) {
                    throw new APIServiceException(
                            APIServiceException.ErrorCode.REVIEW_COMPUTATION_FAILED,
                            "Review computation stage failed for checklist item: " + item,
                            ex);
                }
            }
            emitProgress(progressListener, "COMPUTATION", "Computation stage completed");

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "SUMMARY", "Running summary stage");
            SummaryAgentOutput reviewResult;
            try {
                reviewResult = summaryAgent.execute(new SummaryAgentInput(codeChanges, codeAnalysis, itemAnswers));
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_SUMMARY_FAILED, ex);
            }
            emitProgress(progressListener, "SUMMARY", "Summary stage completed");

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "PIPELINE", "Writing review report");
            CodeReviewReport review = new CodeReviewReport(
                    prData.prId(),
                    prData.title(),
                    reviewResult,
                    interpretation,
                    itemAnswers);
            Path reportPath = writeReport(review);

            emitProgress(
                    progressListener,
                    "PIPELINE",
                    "Review pipeline completed, report generated at: " + reportPath.toString());

            return new CodeReviewOutput(review, reportPath);
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
                    "Code review pipeline execution failed",
                    ex);
        }
    }

    private static void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.REVIEW_CANCELLED,
                    "Review task cancelled by client");
        }
    }

    private static void emitProgress(Consumer<TaskProgressEvent> progressListener, String stage, String message) {
        logger.info("stage={} message={}", stage, message);
        if (progressListener == null) {
            return;
        }
        progressListener.accept(new TaskProgressEvent(false, stage, message));
    }

    private Path writeReport(CodeReviewReport report) {
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);
            String timestamp = REPORT_TIMESTAMP_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()));
            String fileName = "PR" + report.prId() + "_" + timestamp + ".md";
            Path reportPath = dir.resolve(fileName);
            Files.writeString(reportPath, report.toString());
            return reportPath;
        } catch (IOException e) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.REVIEW_REPORT_WRITE_FAILED,
                    "Failed to write review report",
                    e);
        }
    }
}
