package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.llmcr.agent.base.SingleCallAgent;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.service.rag.QueryContextRetriever;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.AdaptiveKStrategy;

@Component
public class QuestionAnswerAgent extends
        SingleCallAgent<String, String> {

    private static final String SYSTEM_PROMPT = """
            You are a software engineering assistant.

            Your task is to answer the user's query using the provided project context.
            Do not make any assumptions or use any information that is not included in the provided context, even if it seems obvious to you as a software engineer. If the answer cannot be found in the provided context, say you don't know instead of trying to infer or guess.
            """;

    private static final String INITIAL_USER_MESSAGE_TEMPLATE = """
            User query:
            <query>

            Retrieved project context:
            <context>

            Answer:
            """;

    private static final String AGENT_NAME = "questionAnswering";
    private final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION;
    private final QueryContextRetriever QUERY_CONTEXT_RETRIEVER;

    public QuestionAnswerAgent(
            ApplicationProperties applicationProperties,
            ModelClientFactory modelClientFactory,
            QueryContextRetriever queryContextRetriever) {
        super(AGENT_NAME, applicationProperties, modelClientFactory, null);

        this.RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
                10,
                new AdaptiveKStrategy(),
                applicationProperties.getAgents().get(AGENT_NAME).getCollection(),
                false);
        this.QUERY_CONTEXT_RETRIEVER = queryContextRetriever;
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
    protected Map<String, Object> buildInputVariables(String query) {
        String contextText = retrieveContext(query);
        return Map.of("query", query, "context", contextText);
    }

    private String retrieveContext(String query) {
        if (query == null || query.isBlank()) {
            return "(no query provided)";
        }

        List<ContextScorePair> retrievedContexts = QUERY_CONTEXT_RETRIEVER
                .retrieve(new ContextRetrievalRequest(List.of(query), RETRIEVAL_CONFIGURATION));

        if (retrievedContexts.isEmpty()) {
            return "(no relevant context retrieved)";
        }

        return String.join("\n---\n", retrievedContexts.stream()
                .map(pair -> pair.context().getContent())
                .toList());
    }

}
