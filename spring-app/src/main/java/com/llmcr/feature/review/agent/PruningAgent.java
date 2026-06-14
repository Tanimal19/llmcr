package com.llmcr.feature.review.agent;

import com.llmcr.config.provider.AgentConfigProvider;
import com.llmcr.feature.review.CodeReviewReport.CodeChange;
import com.llmcr.feature.review.CodeReviewReport.IssueDraft;
import com.llmcr.feature.review.CodeReviewReport.IssueVerdict;
import com.llmcr.infrastructure.agent.BaseAgent;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class PruningAgent
    extends BaseAgent<
        PruningAgent.PruningAgentInput, PruningAgent.PruningModelResponse, IssueVerdict> {

  public record PruningAgentInput(IssueDraft issueDraft, List<CodeChange> codeChanges) {}

  public record PruningModelResponse(
      boolean needsAdditionalData,
      @Nullable String verdict,
      @Nullable String confidence,
      @Nullable String reason,
      @Nullable List<String> evidence,
      @Nullable String dataQuery,
      @Nullable String intermediateAnalysis) {}

  private static final String SYSTEM_PROMPT =
      """
            You are a code review validator. You will be given a single potential issue flagged during code review. Your job is to determine whether this issue actually applies to this project.

            ## Decision Rules

            Confirm the issue if:
            - The assumption holds true given the project context
            - No existing mechanism already handles this concern
            - The risk is real given how this code is actually used

            Dismiss the issue if:
            - The assumption is false for this project
            - An existing pattern, framework, or wrapper already mitigates this
            - The flagged code is unreachable, test-only, or intentionally temporary

            Mark as needs-discussion if:
            - You found conflicting signals (e.g., mitigation exists but is inconsistent)
            - The concern is valid but is a deliberate tradeoff that should be acknowledged

            Request additional data if:
            - The issue relies on an assumption that isn't clearly supported or refuted by the provided context
            - You need more information about the code changes or project context to make a confident decision

            ## Output Format

            When you have a final verdict, output:
            {
                "verdict": "confirmed | dismissed | needs-discussion",
                "confidence": "high | medium | low",
                "reason": "one paragraph explaining the verdict",
                "evidence": ["what you found and from where in the code/context that supports your verdict"],
                "needsAdditionalData": false,
            }

            If you need more information to make a verdict, output:
            {
                "intermediateAnalysis": "your analysis so far based on the currently available information, which can be used to provide context when requesting additional data"
                "dataQuery": "a specific query describing what additional information you need to make a verdict, such as 'What are the usages of method A?' or 'What is class B intended for?'",
                "needsAdditionalData": true,
            }

            You should output JSON only, and strictly follow the output format. Do NOT include any explanations or comments outside the JSON structure. Every string should be wrapped in double quotes.
            """;

  private static final String INITIAL_USER_MESSAGE_TEMPLATE =
      """
            **Issue to Validate:**
            <issue>

            **Code Changes (diff):**
            <code_changes>
            """;

  private static final String AGENT_NAME = "computation";
  private final RetrievalAgent retrievalAgent;

  public PruningAgent(
      AgentConfigProvider configProvider,
      ModelClientFactory modelClientFactory,
      RetrievalAgent retrievalAgent) {
    super(configProvider, modelClientFactory);
    this.retrievalAgent = retrievalAgent;
  }

  @Override
  protected String getAgentName() {
    return AGENT_NAME;
  }

  @Override
  protected Class<PruningModelResponse> getOutputClass() {
    return PruningModelResponse.class;
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
  protected Map<String, Object> buildInputVariables(PruningAgentInput input) {
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

    return Map.of(
        "issue", input.issueDraft().toString(), "code_changes", codeChangesTextBuilder.toString());
  }

  @Override
  protected boolean shouldTerminate(PruningModelResponse response) {
    return !response.needsAdditionalData();
  }

  @Override
  protected Message buildNextUserMessage(int iteration, PruningModelResponse response) {
    if (iteration >= getMaxIterations() - 1) {
      return new UserMessage(
          "THIS IS YOUR FINAL ITERATION. You must provide a final verdict in the following format without requesting additional data: {\"verdict\": \"confirmed | dismissed | needs-discussion\", \"confidence\": \"high | medium | low\", \"reason\": \"one paragraph explaining the verdict\", \"evidence\": [\"what you found and from where in the code/context that supports your verdict\"], \"needsAdditionalData\": false}");
    }

    if (response.dataQuery() == null) {
      return new UserMessage(
          "Your previous analysis indicated that additional data is needed, but the dataQuery is empty.");
    }

    String retrievalResult = retrievalAgent.execute(response.dataQuery());

    StringBuilder messageBuilder = new StringBuilder();
    messageBuilder
        .append("You previously indicated that you need additional data to make a verdict. ")
        .append("Your data query was: ")
        .append(response.dataQuery())
        .append("\n")
        .append("The retrieval data is:\n")
        .append(retrievalResult)
        .append("\n");
    return new UserMessage(messageBuilder.toString());
  }

  @Override
  protected IssueVerdict buildAgentOutput(PruningModelResponse response) {
    return new IssueVerdict(
        response.verdict(), response.confidence(), response.reason(), response.evidence());
  }
}
