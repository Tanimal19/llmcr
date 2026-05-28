package com.llmcr.feature.review;

import com.llmcr.config.provider.LoggingConfigProvider;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.sse.SseTaskObject;
import com.llmcr.feature.review.CodeReviewReport.*;
import com.llmcr.feature.review.PullRequestParser.PullRequestData;
import com.llmcr.feature.review.agent.*;
import com.llmcr.feature.review.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.feature.review.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.feature.review.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.feature.review.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.feature.review.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.infrastructure.agent.logging.AgentContextLogger;
import java.nio.file.Path;
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

  private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private static final String STAGE_REVIEW = "REVIEW";
  private static final String STAGE_STATIC_ANALYSIS = "STATIC_ANALYSIS";
  private static final String STAGE_INTERPRETATION = "INTERPRETATION";
  private static final String STAGE_PLANNING = "PLANNING";
  private static final String STAGE_COMPUTATION = "COMPUTATION";
  private static final String STAGE_SUMMARY = "SUMMARY";

  private final InterpretationAgent interpretationAgent;
  private final PlanningAgent planningAgent;
  private final ComputationAgent computationAgent;
  private final SummaryAgent summaryAgent;
  private final AgentContextLogger contextLogger;
  private String outputDir;

  public CodeReviewService(
      LoggingConfigProvider configProvider,
      InterpretationAgent interpretationAgent,
      PlanningAgent planningAgent,
      ComputationAgent computationAgent,
      SummaryAgent summaryAgent,
      AgentContextLogger contextLogger) {
    this.outputDir = configProvider.getReviewOutputDirectory();
    this.interpretationAgent = interpretationAgent;
    this.planningAgent = planningAgent;
    this.computationAgent = computationAgent;
    this.summaryAgent = summaryAgent;
    this.contextLogger = contextLogger;
  }

  public record CodeReviewInput(String inputFilePath, Integer jsonlIndex, boolean useMockData) {
    public CodeReviewInput(String inputFilePath, boolean useMockData) {
      this(inputFilePath, null, useMockData);
    }

    public CodeReviewInput(String inputFilePath, int jsonlIndex, boolean useMockData) {
      this(inputFilePath, Integer.valueOf(jsonlIndex), useMockData);
    }
  }

  public record CodeReviewOutput(CodeReviewReport reviewReport, Path reportPath) {}

  public String getTaskName() {
    return "code_review";
  }

  @Override
  public CodeReviewOutput execute(
      CodeReviewInput input,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    Path cacheDirectory = null;
    try {
      checkpoint(cancellationRequested, progressListener, STAGE_REVIEW, "Review pipeline started");
      String prefixDirectory =
          REPORT_TIMESTAMP_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()));
      contextLogger.enableLog(prefixDirectory);

      checkpoint(
          cancellationRequested, progressListener, STAGE_REVIEW, "Parsing pull request data");
      PullRequestData prData =
          CodeReviewSupport.parsePullRequestData(
              input.inputFilePath(), input.jsonlIndex(), input.useMockData());
      emitProgress(
          progressListener,
          STAGE_REVIEW,
          "Starting review for PR #" + prData.prId() + ": " + prData.title());

      List<CodeChange> codeChanges = CodeReviewSupport.toCodeChanges(prData);
      CodeReviewSupport.validatePullRequestSize(codeChanges);

      checkpoint(
          cancellationRequested,
          progressListener,
          STAGE_STATIC_ANALYSIS,
          "Running static analysis on cached Java files");
      throwIfCancelled(cancellationRequested);
      Path cachedDirectory =
          CodeReviewSupport.rebuildChangedJavaFilesToCache(prData, outputDir, prefixDirectory);
      String pmdOutput = StaticAnalysisTools.runPmd(cachedDirectory);
      String checkstyleOutput = StaticAnalysisTools.runCheckstyle(cachedDirectory);
      String codeAnalysis =
          "PMD Output:\n" + pmdOutput + "\nCheckstyle Output:\n" + checkstyleOutput;
      emitProgress(
          progressListener, STAGE_STATIC_ANALYSIS, "Static analysis completed:\n" + codeAnalysis);

      InterpretationPlanResult interpretationPlan =
          runInterpretationAndPlanning(
              input, codeChanges, codeAnalysis, progressListener, cancellationRequested);
      List<ChecklistItem> items =
          runChecklistComputations(
              codeChanges, interpretationPlan.plan(), progressListener, cancellationRequested);
      ReportContent reviewResult =
          runSummaryStage(
              codeChanges, codeAnalysis, items, progressListener, cancellationRequested);

      CodeReviewOutput output =
          finalizeReview(
              prData,
              reviewResult,
              interpretationPlan.interpretation(),
              items,
              prefixDirectory,
              progressListener,
              cancellationRequested);
      return output;
    } catch (APIServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
          "Code review pipeline execution failed",
          ex);
    } finally {
      try {
        CodeReviewSupport.cleanupCacheDirectory(cacheDirectory);
        emitProgress(
            progressListener, STAGE_STATIC_ANALYSIS, "Cleaned cache directory: " + cacheDirectory);
      } catch (Exception ex) {
        emitProgress(
            progressListener,
            STAGE_STATIC_ANALYSIS,
            "Failed to clean cache directory: " + cacheDirectory + ", reason: " + ex.getMessage());
      }
      contextLogger.disableLog();
    }
  }

  private InterpretationPlanResult runInterpretationAndPlanning(
      CodeReviewInput input,
      List<CodeChange> codeChanges,
      String codeAnalysis,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    if (!input.useMockData()) {
      checkpoint(
          cancellationRequested,
          progressListener,
          STAGE_INTERPRETATION,
          "Start interpretation stage");
      InterpretationContent interpretation;
      try {
        interpretation = interpretationAgent.execute(new InterpretationAgentInput(codeChanges));
      } catch (Exception ex) {
        throw new APIServiceException(
            APIServiceException.ErrorCode.REVIEW_INTERPRETATION_FAILED, ex);
      }
      emitProgress(
          progressListener,
          STAGE_INTERPRETATION,
          "Interpretation stage completed:\n%s".formatted(interpretation));

      checkpoint(cancellationRequested, progressListener, STAGE_PLANNING, "Start planning stage");
      PlanningAgentOutput plan;
      try {
        plan =
            planningAgent.execute(
                new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
      } catch (Exception ex) {
        throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_PLANNING_FAILED, ex);
      }
      emitProgress(
          progressListener, STAGE_PLANNING, "Planning stage completed:\n%s".formatted(plan));
      return new InterpretationPlanResult(interpretation, plan);
    }

    throwIfCancelled(cancellationRequested);
    InterpretationContent interpretation = MockReviewData.MOCK_INTERPRETATION_OUTPUT;
    PlanningAgentOutput plan = MockReviewData.MOCK_PLANNING_OUTPUT;
    emitProgress(
        progressListener,
        STAGE_INTERPRETATION,
        "Using mock interpretation:\n%s".formatted(interpretation));
    emitProgress(progressListener, STAGE_PLANNING, "Using mock planning\n%s".formatted(plan));
    return new InterpretationPlanResult(interpretation, plan);
  }

  private List<ChecklistItem> runChecklistComputations(
      List<CodeChange> codeChanges,
      PlanningAgentOutput plan,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    checkpoint(
        cancellationRequested,
        progressListener,
        STAGE_COMPUTATION,
        "Running checklist computations");

    List<ChecklistItem> items = new ArrayList<>();
    int totalItems = plan.checklistItems().size();
    int itemIndex = 0;
    for (String item : plan.checklistItems()) {
      checkpoint(
          cancellationRequested,
          progressListener,
          STAGE_COMPUTATION,
          "Running checklist item " + itemIndex + "/" + totalItems + ": " + item);
      itemIndex++;

      try {
        ChecklistItemAnswer answer =
            computationAgent.execute(new ComputationAgentInput(codeChanges, item));
        items.add(new ChecklistItem(item, answer));
        emitProgress(
            progressListener,
            STAGE_COMPUTATION,
            "Completed checklist item, answer:\n%s".formatted(answer));
      } catch (Exception ex) {
        throw new APIServiceException(
            APIServiceException.ErrorCode.REVIEW_COMPUTATION_FAILED,
            "Review computation stage failed for checklist item: " + item,
            ex);
      }
    }
    emitProgress(progressListener, STAGE_COMPUTATION, "Computation stage completed");
    return items;
  }

  private ReportContent runSummaryStage(
      List<CodeChange> codeChanges,
      String codeAnalysis,
      List<ChecklistItem> items,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    checkpoint(cancellationRequested, progressListener, STAGE_SUMMARY, "Running summary stage");
    try {
      ReportContent reviewResult =
          summaryAgent.execute(new SummaryAgentInput(codeChanges, codeAnalysis, items));
      emitProgress(progressListener, STAGE_SUMMARY, "Summary stage completed");
      return reviewResult;
    } catch (Exception ex) {
      throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_SUMMARY_FAILED, ex);
    }
  }

  private CodeReviewOutput finalizeReview(
      PullRequestData prData,
      ReportContent reviewResult,
      InterpretationContent interpretation,
      List<ChecklistItem> items,
      String prefixDirectory,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    checkpoint(cancellationRequested, progressListener, STAGE_REVIEW, "Writing review report");
    CodeReviewReport review =
        new CodeReviewReport(prData.prId(), prData.title(), reviewResult, interpretation, items);
    Path reportPath = CodeReviewSupport.writeReport(review, outputDir, prefixDirectory);

    emitProgress(
        progressListener,
        STAGE_REVIEW,
        "Review pipeline completed, report generated at: " + reportPath.toString());

    return new CodeReviewOutput(review, reportPath);
  }

  private void checkpoint(
      BooleanSupplier cancellationRequested,
      Consumer<SseTaskProgress> progressListener,
      String stage,
      String message) {
    throwIfCancelled(cancellationRequested);
    emitProgress(progressListener, stage, message);
  }

  private record InterpretationPlanResult(
      InterpretationContent interpretation, PlanningAgentOutput plan) {}
}
