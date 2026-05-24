package com.llmcr.service.review;

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

import com.llmcr.agent.ComputationAgent;
import com.llmcr.agent.InterpretationAgent;
import com.llmcr.agent.PlanningAgent;
import com.llmcr.agent.SummaryAgent;
import com.llmcr.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.agent.ComputationAgent.ComputationAgentOutput;
import com.llmcr.agent.ComputationAgent.EvidenceItem;
import com.llmcr.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.agent.SummaryAgent.Issue;
import com.llmcr.agent.SummaryAgent.ItemAnswer;
import com.llmcr.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.agent.SummaryAgent.SummaryAgentOutput;
import com.llmcr.api.APIServiceException;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.service.review.PullRequestParser.PullRequestData;

@Service
public class CodeReviewService {

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

    public record CodeChange(String filePath, String diffContent) {
    }

    public record CodeReviewReport(
            int prId,
            String prTitle,
            SummaryAgentOutput mainReport,
            InterpretationAgentOutput interpretation,
            List<ItemAnswer> itemAnswers) {

        public String toString() {
            return buildMarkdownReport(this);
        }

        private static String buildMarkdownReport(CodeReviewReport report) {
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
                if (report.mainReport().implementationDetails() != null
                        && !report.mainReport().implementationDetails().isEmpty()) {
                    for (var detailsByFile : report.mainReport().implementationDetails()) {
                        String filename = detailsByFile.filename() != null ? detailsByFile.filename()
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
                        sb.append("| ").append(type)
                                .append(" | ").append(issue.title())
                                .append(" | ").append(location)
                                .append(" | ").append(issue.detail())
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
                        sb.append("- ").append(evdience.file()).append(":::").append(evdience.lines()).append(":::")
                                .append(evdience.reason()).append("\n");
                    }
                }
            } else {
                sb.append("_No checklist item answers available._\n\n");
            }

            return sb.toString();
        }
    }

    public record CodeReviewOutput(
            CodeReviewReport reviewReport,
            Path reportPath) {
    }

    public record ReviewStageProgress(
            String stage,
            String status,
            String message) {
    }

    public CodeReviewOutput review(String jsonFilePath, boolean useMockData) {
        return review(jsonFilePath, useMockData, null);
    }

    public CodeReviewOutput review(String jsonFilePath, boolean useMockData,
            Consumer<ReviewStageProgress> progressListener) {
        return review(jsonFilePath, useMockData, progressListener, () -> false);
    }

    public CodeReviewOutput review(String jsonFilePath, boolean useMockData,
            Consumer<ReviewStageProgress> progressListener,
            BooleanSupplier cancellationRequested) {
        try {
            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "PIPELINE", "STARTED", "Review pipeline started");

            if (useMockData) {
                jsonFilePath = MockReviewData.MOCK_PULL_REQUEST_JSON_PATH;
            }

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "PARSE", "STARTED", "Parsing pull request data");
            PullRequestData prData;
            try {
                prData = PullRequestParser.parseJsonFile(jsonFilePath);
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_PARSE_FAILED,
                        "Failed to parse pull request data: " + jsonFilePath, ex);
            }
            emitProgress(progressListener, "PARSE", "COMPLETED",
                    "Parsed pull request data for PR " + prData.prId());

            logger.info("[REVIEW] start prId={} title={}", prData.prId(), prData.title());

            List<CodeChange> codeChanges = prData.changedFiles().stream()
                    .map(file -> new CodeChange(file.path(), file.patch()))
                    .toList();

            // TODO: integrate static analysis tool and populate codeAnalysis
            String codeAnalysis = null;

            InterpretationAgentOutput interpretation;
            PlanningAgentOutput planning;
            if (!useMockData) {
                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, "INTERPRETATION", "STARTED",
                        "Running interpretation stage");
                logger.info("[REVIEW] interpretation:start");
                try {
                    interpretation = interpretationAgent.execute(
                            new InterpretationAgentInput(codeChanges));
                } catch (Exception ex) {
                    throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_INTERPRETATION_FAILED,
                            "Review interpretation stage failed", ex);
                }
                emitProgress(progressListener, "INTERPRETATION", "COMPLETED",
                        "Interpretation stage completed");

                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, "PLANNING", "STARTED",
                        "Running planning stage");
                logger.info("[REVIEW] planning:start");
                try {
                    planning = planningAgent.execute(
                            new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
                } catch (Exception ex) {
                    throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_PLANNING_FAILED,
                            "Review planning stage failed", ex);
                }
                emitProgress(progressListener, "PLANNING", "COMPLETED",
                        "Planning stage completed");
            } else {
                throwIfCancelled(cancellationRequested);
                interpretation = MockReviewData.MOCK_INTERPRETATION;
                planning = MockReviewData.MOCK_PLANNING;
                logger.info("[REVIEW] using mock interpretation/planning");
                emitProgress(progressListener, "INTERPRETATION", "COMPLETED",
                        "Using mock interpretation");
                emitProgress(progressListener, "PLANNING", "COMPLETED",
                        "Using mock planning");
            }

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "COMPUTATION", "STARTED",
                    "Running checklist computations");
            logger.info("[REVIEW] computation:start items={}", planning.checklistItems().size());
            List<ItemAnswer> itemAnswers = new ArrayList<>();
            int totalItems = planning.checklistItems().size();
            int itemIndex = 0;
            for (String item : planning.checklistItems()) {
                throwIfCancelled(cancellationRequested);
                itemIndex++;
                emitProgress(progressListener, "COMPUTATION", "IN_PROGRESS",
                        "Checklist item " + itemIndex + "/" + totalItems + ": " + item);
                logger.debug("[REVIEW] computation:item={}", item);
                ComputationAgentOutput answer;
                try {
                    answer = computationAgent.execute(new ComputationAgentInput(codeChanges, item));
                } catch (Exception ex) {
                    throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_COMPUTATION_FAILED,
                            "Review computation stage failed for checklist item: " + item, ex);
                }
                itemAnswers.add(new ItemAnswer(item, answer));
            }
            emitProgress(progressListener, "COMPUTATION", "COMPLETED",
                    "Computation stage completed");

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "SUMMARY", "STARTED",
                    "Running summary stage");
            logger.info("[REVIEW] summary:start");
            SummaryAgentOutput reviewResult;
            try {
                reviewResult = summaryAgent.execute(
                        new SummaryAgentInput(codeChanges, codeAnalysis, itemAnswers));
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_SUMMARY_FAILED,
                        "Review summary stage failed", ex);
            }
            emitProgress(progressListener, "SUMMARY", "COMPLETED",
                    "Summary stage completed");

            throwIfCancelled(cancellationRequested);
            CodeReviewReport review = new CodeReviewReport(
                    prData.prId(), prData.title(), reviewResult, interpretation, itemAnswers);
            emitProgress(progressListener, "WRITE_REPORT", "STARTED",
                    "Writing review report");
            Path reportPath = writeReport(review);
            emitProgress(progressListener, "WRITE_REPORT", "COMPLETED",
                    "Review report written to " + reportPath);
            emitProgress(progressListener, "PIPELINE", "COMPLETED",
                    "Review pipeline completed");

            logger.info("[REVIEW] done reportPath={}", reportPath);

            return new CodeReviewOutput(review, reportPath);
        } catch (APIServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (Thread.currentThread().isInterrupted()) {
                throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_CANCELLED,
                        "Review task cancelled by client", ex);
            }
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
                    "Code review pipeline execution failed", ex);
        }
    }

    private static void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_CANCELLED,
                    "Review task cancelled by client");
        }
    }

    private static void emitProgress(
            Consumer<ReviewStageProgress> progressListener,
            String stage,
            String status,
            String message) {
        if (progressListener == null) {
            return;
        }
        progressListener.accept(new ReviewStageProgress(stage, status, message));
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
            throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_REPORT_WRITE_FAILED,
                    "Failed to write review report", e);
        }
    }

}
