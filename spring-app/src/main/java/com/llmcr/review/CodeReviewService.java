package com.llmcr.review;

import com.llmcr.api.APIServiceException;
import com.llmcr.api.sse.SseTaskObject;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.review.CodeReviewReport.ChecklistItem;
import com.llmcr.review.CodeReviewReport.ChecklistItemAnswer;
import com.llmcr.review.CodeReviewReport.CodeChange;
import com.llmcr.review.CodeReviewReport.InterpretationContent;
import com.llmcr.review.CodeReviewReport.ReportContent;
import com.llmcr.review.PullRequestParser.PullRequestData;
import com.llmcr.review.agent.ComputationAgent;
import com.llmcr.review.agent.InterpretationAgent;
import com.llmcr.review.agent.PlanningAgent;
import com.llmcr.review.agent.SummaryAgent;
import com.llmcr.review.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.review.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.review.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.review.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.review.agent.SummaryAgent.SummaryAgentInput;
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
        extends SseTaskObject<CodeReviewService.CodeReviewInput, CodeReviewService.CodeReviewOutput> {

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

    public String getTaskName() {
        return "code_review";
    }

    @Override
    public CodeReviewOutput execute(
            CodeReviewInput input,
            Consumer<SseTaskProgress> progressListener,
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

            InterpretationContent interpretation;
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

            List<ChecklistItem> items = new ArrayList<>();
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
                    ChecklistItemAnswer answer = computationAgent.execute(
                            new ComputationAgentInput(codeChanges, item));
                    items.add(new ChecklistItem(item, answer));
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
            ReportContent reviewResult;
            try {
                reviewResult = summaryAgent.execute(new SummaryAgentInput(codeChanges, codeAnalysis, items));
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
                    items);
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
