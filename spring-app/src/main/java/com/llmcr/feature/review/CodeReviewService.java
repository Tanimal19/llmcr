package com.llmcr.feature.review;

import com.llmcr.config.provider.LoggingConfigProvider;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.sse.SseTaskObject;
import com.llmcr.feature.review.CodeReviewReport.*;
import com.llmcr.feature.review.PullRequestParser.PullRequestData;
import com.llmcr.feature.review.agent.*;
import com.llmcr.feature.review.agent.DraftingAgent.DraftingAgentInput;
import com.llmcr.feature.review.agent.DraftingAgent.DraftingAgentOutput;
import com.llmcr.feature.review.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.feature.review.agent.PruningAgent.PruningAgentInput;
import com.llmcr.feature.review.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.feature.review.agent.SummaryAgent.SummaryAgentOutput;
import com.llmcr.feature.review.agent.tool.StaticAnalysisToolManager;
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

  private static final String STAGE_PARSE = "PARSE";
  private static final String STAGE_STATIC_ANALYSIS = "STATIC_ANALYSIS";
  private static final String STAGE_INTERPRETATION = "INTERPRETATION";
  private static final String STAGE_DRAFTING = "DRAFTING";
  private static final String STAGE_PRUNING = "PRUNING";
  private static final String STAGE_SUMMARY = "SUMMARY";

  private final StaticAnalysisToolManager staticAnalysisToolManager;
  private final InterpretationAgent interpretationAgent;
  private final DraftingAgent draftingAgent;
  private final PruningAgent pruningAgent;
  private final SummaryAgent summaryAgent;
  private final AgentContextLogger contextLogger;
  private String outputDir;

  public CodeReviewService(
      LoggingConfigProvider configProvider,
      StaticAnalysisToolManager staticAnalysisToolManager,
      InterpretationAgent interpretationAgent,
      DraftingAgent draftingAgent,
      PruningAgent pruningAgent,
      SummaryAgent summaryAgent,
      AgentContextLogger contextLogger) {
    this.outputDir = configProvider.getReviewOutputDirectory();
    this.staticAnalysisToolManager = staticAnalysisToolManager;
    this.interpretationAgent = interpretationAgent;
    this.draftingAgent = draftingAgent;
    this.pruningAgent = pruningAgent;
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
    try {
      String prefixDirectory = beginReviewPipeline(progressListener, cancellationRequested);
      contextLogger.enableLog(prefixDirectory);

      ParseStageResult parseStageResult =
          runParseStage(input, progressListener, cancellationRequested);
      String codeAnalysisOutput =
          runStaticAnalysisStage(
              parseStageResult.prData(), prefixDirectory, progressListener, cancellationRequested);
      // String codeAnalysisOutput = null;

      InterpretationContent interpretation =
          runInterpretationStage(
              input, parseStageResult.codeChanges(), progressListener, cancellationRequested);
      List<IssueDraft> draftIssues =
          runDraftingStage(
              input,
              parseStageResult.codeChanges(),
              interpretation,
              progressListener,
              cancellationRequested);
      List<Issue> issues =
          runPruningStage(
              parseStageResult.codeChanges(), draftIssues, progressListener, cancellationRequested);
      SummaryAgentOutput reviewResult =
          runSummaryStage(
              parseStageResult.codeChanges(),
              interpretation,
              issues,
              progressListener,
              cancellationRequested);

      return runFinalizeStage(
          parseStageResult.prData(),
          reviewResult,
          interpretation,
          issues,
          codeAnalysisOutput,
          prefixDirectory,
          progressListener,
          cancellationRequested);
    } catch (APIServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
          "Code review pipeline execution failed",
          ex);
    } finally {
      contextLogger.disableLog();
    }
  }

  private String beginReviewPipeline(
      Consumer<SseTaskProgress> progressListener, BooleanSupplier cancellationRequested) {
    checkpoint(cancellationRequested, progressListener, STAGE_PARSE, "Review pipeline started");
    return REPORT_TIMESTAMP_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()));
  }

  private ParseStageResult runParseStage(
      CodeReviewInput input,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    checkpoint(cancellationRequested, progressListener, STAGE_PARSE, "Parsing pull request data");
    try {
      PullRequestData prData =
          CodeReviewSupport.parsePullRequestData(
              input.inputFilePath(), input.jsonlIndex(), input.useMockData());
      emitProgress(
          progressListener,
          STAGE_PARSE,
          "Starting review for PR #" + prData.prId() + ": " + prData.title());

      List<CodeChange> codeChanges = CodeReviewSupport.toCodeChanges(prData);
      CodeReviewSupport.validatePullRequestSize(codeChanges);
      return new ParseStageResult(prData, codeChanges);
    } catch (APIServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PARSE_FAILED, "Failed during parse stage", ex);
    }
  }

  private String runStaticAnalysisStage(
      PullRequestData prData,
      String prefixDirectory,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    checkpoint(
        cancellationRequested,
        progressListener,
        STAGE_STATIC_ANALYSIS,
        "Running static analysis on cached Java files");

    try {
      throwIfCancelled(cancellationRequested);
      Path cachedDirectory =
          CodeReviewSupport.rebuildChangedJavaFilesToCache(prData, outputDir, prefixDirectory);

      String codeAnalysisOutput = staticAnalysisToolManager.runStaticAnalysisTools(cachedDirectory);
      emitProgress(
          progressListener,
          STAGE_STATIC_ANALYSIS,
          "Static analysis completed:\n" + codeAnalysisOutput);

      CodeReviewSupport.cleanupCacheDirectory(cachedDirectory);

      return codeAnalysisOutput;
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_STATIC_ANALYSIS_FAILED,
          "Failed during static analysis stage",
          ex);
    }
  }

  private InterpretationContent runInterpretationStage(
      CodeReviewInput input,
      List<CodeChange> codeChanges,
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
      return interpretation;
    }

    throwIfCancelled(cancellationRequested);
    InterpretationContent interpretation = MockReviewData.MOCK_INTERPRETATION_OUTPUT;
    emitProgress(
        progressListener,
        STAGE_INTERPRETATION,
        "Using mock interpretation:\n%s".formatted(interpretation));
    return interpretation;
  }

  private List<IssueDraft> runDraftingStage(
      CodeReviewInput input,
      List<CodeChange> codeChanges,
      InterpretationContent interpretation,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    if (!input.useMockData()) {
      checkpoint(cancellationRequested, progressListener, STAGE_DRAFTING, "Start drafting stage");
      DraftingAgentOutput draft;
      try {
        draft = draftingAgent.execute(new DraftingAgentInput(codeChanges, interpretation));
      } catch (Exception ex) {
        throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_PLANNING_FAILED, ex);
      }
      emitProgress(
          progressListener, STAGE_DRAFTING, "Drafting stage completed:\n%s".formatted(draft));
      return draft.issueDrafts();
    }

    throwIfCancelled(cancellationRequested);
    DraftingAgentOutput draft = MockReviewData.MOCK_DRAFTING_OUTPUT;
    emitProgress(progressListener, STAGE_DRAFTING, "Using mock drafting\n%s".formatted(draft));
    return draft.issueDrafts();
  }

  private List<Issue> runPruningStage(
      List<CodeChange> codeChanges,
      List<IssueDraft> draftIssues,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    checkpoint(cancellationRequested, progressListener, STAGE_PRUNING, "Running pruning stage");

    List<Issue> issues = new ArrayList<>();
    int totalIssues = draftIssues.size();
    int issueIndex = 0;
    for (IssueDraft draftIssue : draftIssues) {
      checkpoint(
          cancellationRequested,
          progressListener,
          STAGE_PRUNING,
          "Pruning issue " + issueIndex + "/" + totalIssues + ": " + draftIssue.title());
      issueIndex++;

      try {
        IssueVerdict verdict = pruningAgent.execute(new PruningAgentInput(draftIssue, codeChanges));
        issues.add(new Issue(draftIssue, verdict));
        emitProgress(
            progressListener,
            STAGE_PRUNING,
            "Completed pruning issue, verdict:\n%s".formatted(verdict));
      } catch (Exception ex) {
        throw new APIServiceException(
            APIServiceException.ErrorCode.REVIEW_COMPUTATION_FAILED,
            "Pruning stage failed for issue: " + draftIssue.title(),
            ex);
      }
    }
    emitProgress(progressListener, STAGE_PRUNING, "Pruning stage completed");
    return issues;
  }

  private SummaryAgentOutput runSummaryStage(
      List<CodeChange> codeChanges,
      InterpretationContent interpretation,
      List<Issue> issues,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    checkpoint(cancellationRequested, progressListener, STAGE_SUMMARY, "Running summary stage");
    try {
      SummaryAgentOutput reviewResult =
          summaryAgent.execute(new SummaryAgentInput(codeChanges, interpretation, issues));
      emitProgress(progressListener, STAGE_SUMMARY, "Summary stage completed");
      return reviewResult;
    } catch (Exception ex) {
      throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_SUMMARY_FAILED, ex);
    }
  }

  private CodeReviewOutput runFinalizeStage(
      PullRequestData prData,
      SummaryAgentOutput reviewResult,
      InterpretationContent interpretation,
      List<Issue> issues,
      String staticAnalysisResults,
      String prefixDirectory,
      Consumer<SseTaskProgress> progressListener,
      BooleanSupplier cancellationRequested) {
    checkpoint(cancellationRequested, progressListener, STAGE_PARSE, "Writing review report");
    try {
      ReviewReportContent content =
          new ReviewReportContent(
              reviewResult.motivation(),
              reviewResult.suggestion(),
              reviewResult.goodPoints(),
              reviewResult.badPoints(),
              reviewResult.implementationDetails(),
              issues);
      CodeReviewReport review =
          new CodeReviewReport(
              prData.prId(), prData.title(), interpretation, content, staticAnalysisResults);
      Path reportPath = CodeReviewSupport.writeReport(review, outputDir, prefixDirectory);

      emitProgress(
          progressListener,
          STAGE_PARSE,
          "Review pipeline completed, report generated at: " + reportPath);

      return new CodeReviewOutput(review, reportPath);
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_REPORT_WRITE_FAILED,
          "Failed while finalizing review report",
          ex);
    }
  }

  private void checkpoint(
      BooleanSupplier cancellationRequested,
      Consumer<SseTaskProgress> progressListener,
      String stage,
      String message) {
    throwIfCancelled(cancellationRequested);
    emitProgress(progressListener, stage, message);
  }

  private record ParseStageResult(PullRequestData prData, List<CodeChange> codeChanges) {}
}
