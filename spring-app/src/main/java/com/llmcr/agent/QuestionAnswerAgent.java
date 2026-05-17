package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.SingleCallAgent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.service.rag.QueryContextRetriever;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.AdaptiveKStrategy;

@Component
public class QuestionAnswerAgent extends
        SingleCallAgent<String, String> {

    private static final String PROMPT_TEMPLATE = """
            You are a software engineering assistant.

            Your task is to answer the user's query using the provided project context.
            Do not make any assumptions or use any information that is not included in the provided context, even if it seems obvious to you as a software engineer. If the answer cannot be found in the provided context, say you don't know instead of trying to infer or guess.

            User query:
            <query>

            Retrieved project context:
            <context>

            Answer:
            """;

    private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
            10, new AdaptiveKStrategy(), "all", false);

    private final QueryContextRetriever queryContextRetriever;

    public QuestionAnswerAgent(
            @Value("${llmcr.agent.question-answer.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.question-answer.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            QueryContextRetriever queryContextRetriever) {
        super(chatProviderName, chatModelName, modelClientFactory, null);
        this.queryContextRetriever = queryContextRetriever;
    }

    @Override
    protected String getPromptTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    protected Map<String, Object> getPromptVariables(String query) {
        String contextText = retrieveContext(query);
        return Map.of("query", query, "context", contextText);
    }

    @Override
    protected String convertModelResponse(String response) {
        return response.trim();
    }

    private String retrieveContext(String query) {
        if (query == null || query.isBlank()) {
            return "(no query provided)";
        }

        List<ContextScorePair> retrievedContexts = queryContextRetriever
                .retrieve(new ContextRetrievalRequest(List.of(query), RETRIEVAL_CONFIGURATION));

        if (retrievedContexts.isEmpty()) {
            return "(no relevant context retrieved)";
        }

        return String.join("\n---\n", retrievedContexts.stream()
                .map(pair -> pair.context().getContent())
                .toList());
    }

}
