package com.llmcr.feature.review.agent;

import com.llmcr.config.provider.AgentConfigProvider;
import com.llmcr.feature.review.CodeReviewReport.CodeChange;
import com.llmcr.feature.review.CodeReviewReport.ImplementationDetails;
import com.llmcr.feature.review.CodeReviewReport.InterpretationContent;
import com.llmcr.feature.review.CodeReviewReport.Issue;
import com.llmcr.infrastructure.agent.SingleCallAgent;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SummaryAgent
    extends SingleCallAgent<SummaryAgent.SummaryAgentInput, SummaryAgent.SummaryAgentOutput> {

  public record SummaryAgentInput(
      List<CodeChange> codeChanges,
      InterpretationContent codeInterpretation,
      List<Issue> issues,
      String staticAnalysisResults) {}

  public record SummaryAgentOutput(
      String motivation,
      String suggestion,
      List<String> goodPoints,
      List<String> badPoints,
      List<ImplementationDetails> implementationDetails) {}

  private static final String SYSTEM_PROMPT =
      """
            You are a senior Java code reviewer writing the final code review report.

            Your goal is to summarize the given data into a structured report the author can use to improve the code change.

            ## Your task
            You will be given a change interpretation, the code changes, a list of validated issues, and static code analysis results. Based on this information, produce:
            - motivation: Why the change was made, summarized from the interpretation.
            - goodPoints: Aspects of the change that are well done.
            - badPoints: Aspects that could be improved but are not significant enough to be raised as issues.
            - suggestion: Concrete, actionable suggestions for improvement, based on the bad points and issues.
            - implementationDetails: Summarize important implementation details that reviewers should pay attention to, such as pattern used, non-obvious design decisions, etc. Grouped by file. Do NOT include actionable feedback here, just factual details.

            You don't need to output issues as they will be appended separately. Focus on summarizing the overall change and providing high-level feedback.

            ## Rules
            - Be concise and specific. Avoid vague and general statements.
            - Do NOT invent new issues — the issue list is already finalized and will be appended separately.
            - Do NOT make assumptions beyond the provided information.

            Think step by step internally before answering.

            ## Output Format
            Output only a JSON object:
            {
                "motivation": "why the change was made",
                "suggestion": "overall suggestion for the PR",
                "goodPoints": ["..."],
                "badPoints": ["..."],
                "implementationDetails": [
                    {"filename": "...", "details": "..."}
                ]
            }
            """;

  private static final String INITIAL_USER_MESSAGE_TEMPLATE =
      """
            **Code Change Description:**
            <change_description>

            **Code Changes (diff):**
            <code_changes>

            **Issues:**
            <issues>

            **Static Analysis Results:**
            <static_analysis_results>
            """;

  private static final String AGENT_NAME = "summary";

  public SummaryAgent(AgentConfigProvider configProvider, ModelClientFactory modelClientFactory) {
    super(configProvider, modelClientFactory);
  }

  @Override
  protected String getAgentName() {
    return AGENT_NAME;
  }

  @Override
  protected Class<SummaryAgentOutput> getOutputClass() {
    return SummaryAgentOutput.class;
  }

  @Override
  protected String getSystemMessage() {
    return SYSTEM_PROMPT;
  }

  @Override
  protected String getInitialUserMessageTemplate() {
    return INITIAL_USER_MESSAGE_TEMPLATE;
  }

  @Override
  protected Map<String, Object> buildInputVariables(SummaryAgentInput input) {
    InterpretationContent interpretation = input.codeInterpretation();
    String descriptionText =
        interpretation.changeMotivation() + "\n" + interpretation.changeDescription();

    StringBuilder codeChangesTextBuilder = new StringBuilder();
    int index = 1;
    for (CodeChange change : input.codeChanges()) {
      codeChangesTextBuilder
          .append("[Code Change ")
          .append(index)
          .append("]\n")
          .append(change.toString())
          .append("\n\n");
      index++;
    }

    StringBuilder issuesTextBuilder = new StringBuilder();
    index = 1;
    for (Issue issue : input.issues()) {
      if (issue.verdict().verdict().toLowerCase().equals("dismissed")) {
        continue; // Skip non-issues
      }
      issuesTextBuilder
          .append("[Issue ")
          .append(index)
          .append("]\n")
          .append(issue.draft().toString())
          .append("\n\n");
      index++;
    }

    return Map.of(
        "change_description",
        descriptionText,
        "code_changes",
        codeChangesTextBuilder.toString(),
        "issues",
        issuesTextBuilder.toString(),
        "static_analysis_results",
        input.staticAnalysisResults());
  }
}
