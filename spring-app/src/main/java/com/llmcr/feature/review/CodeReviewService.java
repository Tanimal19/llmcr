package com.llmcr.feature.review;

import com.llmcr.config.provider.LoggingConfigProvider;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.sse.SseTaskObject;
import com.llmcr.feature.review.agent.*;
import com.llmcr.feature.review.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.feature.review.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.feature.review.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.feature.review.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.feature.review.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.feature.review.CodeReviewReport.*;
import com.llmcr.feature.review.PullRequestParser.PullRequestData;

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
import org.springframework.stereotype.Service;

@Service
public class CodeReviewService
        extends SseTaskObject<CodeReviewService.CodeReviewInput, CodeReviewService.CodeReviewOutput> {

    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String STAGE_REVIEW = "REVIEW";
    private static final String STAGE_INTERPRETATION = "INTERPRETATION";
    private static final String STAGE_PLANNING = "PLANNING";
    private static final String STAGE_COMPUTATION = "COMPUTATION";
    private static final String STAGE_SUMMARY = "SUMMARY";

    private final InterpretationAgent interpretationAgent;
    private final PlanningAgent planningAgent;
    private final ComputationAgent computationAgent;
    private final SummaryAgent summaryAgent;
    private String outputDir;

    public CodeReviewService(
            LoggingConfigProvider configProvider,
            InterpretationAgent interpretationAgent,
            PlanningAgent planningAgent,
            ComputationAgent computationAgent,
            SummaryAgent summaryAgent) {
        this.outputDir = configProvider.getReviewOutputDirectory();
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
            emitProgress(progressListener, STAGE_REVIEW, "Review pipeline started");

            String jsonFilePath = input.useMockData()
                    ? MockReviewData.MOCK_PULL_REQUEST_JSON_PATH
                    : input.jsonFilePath();

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, STAGE_REVIEW, "Parsing pull request data");
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
            PlanningAgentOutput plan;
            if (!input.useMockData()) {
                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, STAGE_INTERPRETATION, "Start interpretation stage");
                try {
                    interpretation = interpretationAgent.execute(new InterpretationAgentInput(codeChanges));
                } catch (Exception ex) {
                    throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_INTERPRETATION_FAILED, ex);
                }
                emitProgress(progressListener, STAGE_INTERPRETATION,
                        "Interpretation stage completed:\n{}".formatted(interpretation));

                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, STAGE_PLANNING, "Start planning stage");
                try {
                    plan = planningAgent.execute(new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
                } catch (Exception ex) {
                    throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_PLANNING_FAILED, ex);
                }
                emitProgress(progressListener, STAGE_PLANNING, "Planning stage completed:\n{}".formatted(plan));
            } else {
                throwIfCancelled(cancellationRequested);
                interpretation = MockReviewData.MOCK_INTERPRETATION_OUTPUT;
                plan = MockReviewData.MOCK_PLANNING_OUTPUT;
                emitProgress(progressListener, STAGE_INTERPRETATION,
                        "Using mock interpretation:\n{}".formatted(interpretation));
                emitProgress(progressListener, STAGE_PLANNING, "Using mock planning\n{}".formatted(plan));
            }

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, STAGE_COMPUTATION, "Running checklist computations");

            List<ChecklistItem> items = new ArrayList<>();
            int totalItems = plan.checklistItems().size();
            int itemIndex = 0;
            for (String item : plan.checklistItems()) {
                throwIfCancelled(cancellationRequested);
                emitProgress(
                        progressListener,
                        STAGE_COMPUTATION,
                        "Running checklist item " + itemIndex + "/" + totalItems + ": " + item);
                itemIndex++;

                try {
                    ChecklistItemAnswer answer = computationAgent.execute(
                            new ComputationAgentInput(codeChanges, item));
                    items.add(new ChecklistItem(item, answer));
                    emitProgress(
                            progressListener,
                            STAGE_COMPUTATION,
                            "Completed checklist item, answer:\n{}".formatted(answer));
                } catch (Exception ex) {
                    throw new APIServiceException(
                            APIServiceException.ErrorCode.REVIEW_COMPUTATION_FAILED,
                            "Review computation stage failed for checklist item: " + item,
                            ex);
                }
            }
            emitProgress(progressListener, STAGE_COMPUTATION, "Computation stage completed");

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, STAGE_SUMMARY, "Running summary stage");
            ReportContent reviewResult;
            try {
                reviewResult = summaryAgent.execute(new SummaryAgentInput(codeChanges, codeAnalysis, items));
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_SUMMARY_FAILED, ex);
            }
            emitProgress(progressListener, STAGE_SUMMARY, "Summary stage completed");

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, STAGE_REVIEW, "Writing review report");
            CodeReviewReport review = new CodeReviewReport(
                    prData.prId(),
                    prData.title(),
                    reviewResult,
                    interpretation,
                    items);
            Path reportPath = writeReport(review);

            emitProgress(
                    progressListener,
                    STAGE_REVIEW,
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
