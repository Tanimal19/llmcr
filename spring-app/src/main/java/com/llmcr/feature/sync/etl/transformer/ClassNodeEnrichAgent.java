package com.llmcr.feature.sync.etl.transformer;

import com.llmcr.config.provider.AgentConfigProvider;
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
public class ClassNodeEnrichAgent
    extends SingleCallAgent<
        ClassNodeEnrichAgent.ClassNodeEnrichInput, ClassNodeEnrichAgent.ClassNodeEnrichOutput> {

  public record ClassNodeEnrichInput(String classContent) {
    private static final int QUERY_CHUNK_SIZE = 2000;

    public List<String> buildQueries() {
      List<String> queries = new ArrayList<>();
      int start = 0;
      while (start < classContent.length()) {
        int end = Math.min(start + QUERY_CHUNK_SIZE, classContent.length());
        queries.add(classContent.substring(start, end));
        start = end;
      }
      return queries;
    }
  }

  public record ClassNodeEnrichOutput(String functional, String relationship, String usage) {}

  private static final String SYSTEM_PROMPT =
      """
            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and its related documentation contents.
            You should generate below information for enrichment:
            - **functional**: What does this class do?
            - **relationship**: How does this class relate to other classes or components in the project?
            - **usage**: A example that show the most important usage scenario of this class, illustrate the one most important example in natural language rather than code.

            Do not make assumptions beyond the provided code and documentation.

            ## Output Format
            {
                "functional": "a concise description of what this class does (max 100 words)",
                "relationship": "a concise description of how this class relates to other classes or components in the project (max 100 words)",
                "usage": "a concise description of the most important usage scenario of this class, illustrate the one most important example in natural language rather than code (max 100 words)"
            }
            """;

  private static final String INITIAL_USER_MESSAGE_TEMPLATE =
      """
            Raw code at below.
            ```java
            <class_content>
            ```

            Documentation contents at below.
            <context>
            """;

  private static final String AGENT_NAME = "class-node-enrich";
  private static final int RETRIEVAL_TOP_K = 10;
  private final QueryContextRetrievalConfig retrievalConfig;
  private final QueryContextRetriever retriever;

  public ClassNodeEnrichAgent(
      AgentConfigProvider configProvider,
      ModelClientFactory modelClientFactory,
      QueryContextRetriever retriever) {
    super(configProvider, modelClientFactory);

    this.retrievalConfig =
        new QueryContextRetrievalConfig(
            configProvider.getAgentCollectionConfig(AGENT_NAME), RETRIEVAL_TOP_K);
    this.retriever = retriever;
  }

  @Override
  protected String getAgentName() {
    return AGENT_NAME;
  }

  @Override
  protected Class<ClassNodeEnrichOutput> getOutputClass() {
    return ClassNodeEnrichOutput.class;
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
  protected Map<String, Object> buildInputVariables(ClassNodeEnrichInput input) {
    String contextText = retrieveContext(input);
    return Map.of("class_content", input.classContent(), "context", contextText);
  }

  private String retrieveContext(ClassNodeEnrichInput input) {
    List<String> queries = input.buildQueries();
    List<ContextScorePair> retrievedContexts =
        retriever.retrieve(new QueryContextRetrievalRequest(queries, retrievalConfig));
    return String.join(
        "\n---\n", retrievedContexts.stream().map(pair -> pair.context().getContent()).toList());
  }
}
