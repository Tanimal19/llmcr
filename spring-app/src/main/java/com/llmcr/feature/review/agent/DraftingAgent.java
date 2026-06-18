package com.llmcr.feature.review.agent;

import com.llmcr.config.provider.AgentConfigProvider;
import com.llmcr.feature.review.CodeReviewReport.CodeChange;
import com.llmcr.feature.review.CodeReviewReport.InterpretationContent;
import com.llmcr.feature.review.CodeReviewReport.IssueDraft;
import com.llmcr.infrastructure.agent.SingleCallAgent;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import com.llmcr.infrastructure.rag.ContextScorePair;
import com.llmcr.infrastructure.rag.QueryContextRetriever;
import com.llmcr.infrastructure.rag.QueryContextRetriever.QueryContextRetrievalConfig;
import com.llmcr.infrastructure.rag.QueryContextRetriever.QueryContextRetrievalRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DraftingAgent
    extends SingleCallAgent<DraftingAgent.DraftingAgentInput, DraftingAgent.DraftingAgentOutput> {

  public record DraftingAgentInput(
      List<CodeChange> codeChanges, InterpretationContent codeInterpretation) {}

  public record DraftingAgentOutput(List<IssueDraft> issueDrafts) {}

  private static final String SYSTEM_PROMPT =
      """
            You are a Java code reviewer performing exhaustive issue discovery.

            Your goal is RECALL, not precision. Surface every potential issue you can identify, even if it might not apply to this specific project. You will not be filtering — a separate system will handle that.

            ## Your task
            You will be given code changes, a change interpretation, and some review guidelines. Identify as many issues as possible across the following dimensions:
            - Compatibility: does the change fit existing code and intended usage scenarios?
            - Design: is the change well-structured and aligned with best practices?
            - Security: does it introduce vulnerabilities?
            - Functionality: does it work as intended?
            - Performance: does it introduce inefficiencies?
            - Maintainability: is it easy to understand and modify later?
            - Readability: is it clear and understandable?

            ## Rules
            - Do NOT filter based on whether you think the project cares about this issue
            - Do NOT cluster similar issues — output each as a separate entry
            - Do NOT suggest fixes — describe problems only

            Think step by step internally before answering.

            ## Output Format
            {
                "issueDrafts": [
                        {
                            "dimension": "Compatibility | Design | Security | Functionality | Performance | Maintainability | Readability",
                            "severity": "Critical | Major | Minor",
                            "location": "filename::line_number",
                            "title": "short issue title (max 10 words)",
                            "description": "what the issue is and why it is a problem",
                            "assumption": "what was assumed for this issue to be valid"
                        },
                        ...
                ]
            }
            """;

  private static final String INITIAL_USER_MESSAGE_TEMPLATE =
      """
            **Code Change Description:**
            <change_description>

            **Code Changes (diff):**
            <code_changes>

            **Review Guidelines:**
            <guidelines>
            """;

  private static final String AGENT_NAME = "drafting";
  private static final int RETRIEVAL_TOP_K = 20;
  private final QueryContextRetrievalConfig retrievalConfig;
  private final QueryContextRetriever retriever;

  public DraftingAgent(
      AgentConfigProvider configProvider,
      ModelClientFactory modelClientFactory,
      QueryContextRetriever retriever) {
    super(configProvider, modelClientFactory);

    this.retrievalConfig = new QueryContextRetrievalConfig(null, RETRIEVAL_TOP_K);
    this.retriever = retriever;
  }

  @Override
  protected String getAgentName() {
    return AGENT_NAME;
  }

  @Override
  protected Class<DraftingAgentOutput> getOutputClass() {
    return DraftingAgentOutput.class;
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
  protected Map<String, Object> buildInputVariables(DraftingAgentInput input) {
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
    String guidelineText = retrieveContext(input, descriptionText);

    return Map.of(
        "change_description",
        descriptionText,
        "code_changes",
        codeChangesTextBuilder.toString(),
        "guidelines",
        guidelineText);
  }

  private String retrieveContext(DraftingAgentInput input, String descriptionText) {
    List<ContextScorePair> retrievedContexts =
        retriever.retrieve(
            new QueryContextRetrievalRequest(List.of(descriptionText), retrievalConfig));

    StringBuilder contextBuilder = new StringBuilder();
    int index = 1;
    for (ContextScorePair pair : retrievedContexts) {
      contextBuilder
          .append("[Guideline ")
          .append(index)
          .append("]\n")
          .append("Score: ")
          .append(pair.score())
          .append("\n")
          .append(pair.context().getContent())
          .append("\n---\n");
      index++;
    }

    return contextBuilder.toString();
  }
}
