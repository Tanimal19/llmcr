package com.llmcr.feature.review.agent;

import com.llmcr.config.provider.AgentConfigProvider;
import com.llmcr.feature.review.CodeReviewReport.CodeChange;
import com.llmcr.feature.review.CodeReviewReport.InterpretationContent;
import com.llmcr.infrastructure.agent.SingleCallAgent;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import com.llmcr.infrastructure.rag.ContextScorePair;
import com.llmcr.infrastructure.rag.QueryContextRetriever;
import com.llmcr.infrastructure.rag.QueryContextRetriever.QueryContextRetrievalConfig;
import com.llmcr.infrastructure.rag.QueryContextRetriever.QueryContextRetrievalRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InterpretationAgent
    extends SingleCallAgent<InterpretationAgent.InterpretationAgentInput, InterpretationContent> {

  public record InterpretationAgentInput(List<CodeChange> codeChanges) {}

  private static final String SYSTEM_PROMPT =
      """
            You are now a software engineer experienced at Java and Spring Framework.

            Your task is to interpret the code change by describing what was changed, and the movitation of the changes.

            You will be given code changes and project context retrieved based on the code changes. The project context may include information such as related code snippets, documentation, discussions, etc. You should make use of the project context when interpreting the code change. Do not make assumptions beyond the provided information. Focus on analyzing the code change based on the given context.

            ## Output Format
            {
                "changeDescription": "a concise description of what was changed and what will be the impact of the change, including any potential downstream effects on other parts of the codebase or system",
                "changeMotivation": "a concise description of why the change was made, including what problem the change is trying to solve and why the original code was insufficient"
            }
            """;

  private static final String INITIAL_USER_MESSAGE_TEMPLATE =
      """
            Below is a list of project context:
            <context>

            Below is the code change you need to interpret:
            <code_changes>
            """;

  private static final String AGENT_NAME = "interpretation";
  private static final int RETRIEVAL_TOP_K = 10;
  private final QueryContextRetriever retriever;
  private final QueryContextRetrievalConfig retrievalConfig;

  public InterpretationAgent(
      AgentConfigProvider configProvider,
      ModelClientFactory modelClientFactory,
      QueryContextRetriever retriever) {
    super(configProvider, modelClientFactory);

    this.retriever = retriever;
    this.retrievalConfig = new QueryContextRetrievalConfig(null, RETRIEVAL_TOP_K);
  }

  @Override
  protected String getAgentName() {
    return AGENT_NAME;
  }

  @Override
  protected Class<InterpretationContent> getOutputClass() {
    return InterpretationContent.class;
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
  protected Map<String, Object> buildInputVariables(InterpretationAgentInput input) {
    String codeChangesText =
        String.join(
            "\n----\n",
            input.codeChanges().stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());
    String contextText = retrieveContext(input);
    return Map.of("code_changes", codeChangesText, "context", contextText);
  }

  private String retrieveContext(InterpretationAgentInput input) {
    List<String> queries = new ArrayList<>();
    for (CodeChange change : input.codeChanges()) {
      queries.add(change.filePath());
      queries.add(change.diffContent());
    }

    List<ContextScorePair> retrievedContexts =
        retriever.retrieve(new QueryContextRetrievalRequest(queries, retrievalConfig));
    return String.join(
        "\n---\n", retrievedContexts.stream().map(pair -> pair.context().getContent()).toList());
  }
}
