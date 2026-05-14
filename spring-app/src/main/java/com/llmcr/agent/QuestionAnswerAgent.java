package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.RecursiveAgent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.service.rag.QueryContextRetriever;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.AdaptiveKStrategy;

@Component
public class QuestionAnswerAgent extends
        RecursiveAgent<String, QuestionAnswerAgent.ModelResponse, String> {

    public record ModelResponse(
            String answer,
            String analysis,
            boolean needsAdditionalData,
            String dataQuery) {
    }

    private static final String PROMPT_TEMPLATE = """
            You are a software engineering assistant.

            Your task is to answer the user's query using only the provided project context and any additional data you retrieve later.

            Rules:
            - Use only explicitly provided information.
            - Do not make assumptions.
            - If information is insufficient, set needsAdditionalData=true and provide a clear dataQuery.
            - Keep analysis concise and evidence-based.

            Output format (JSON only):
            {
              "answer": "...",
              "analysis": "...",
              "needsAdditionalData": false,
              "dataQuery": null
            }

            When information is insufficient:
            {
              "answer": null,
              "analysis": "The provided context is insufficient to answer the query.",
              "needsAdditionalData": true,
              "dataQuery": "..."
            }

            You should output JSON only, and strictly follow the output format.

            User query:
            <query>

            Retrieved project context:
            <context>
            """;

    private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
            10, new AdaptiveKStrategy(), "project-context", false);

    private final QueryContextRetriever queryContextRetriever;
    private final RetrievalAgent retrievalAgent;

    public QuestionAnswerAgent(
            @Value("${llmcr.agent.question-answer.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.question-answer.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            QueryContextRetriever queryContextRetriever,
            RetrievalAgent retrievalAgent) {
        super(chatProviderName, chatModelName, modelClientFactory,
                new BeanOutputConverter<>(ModelResponse.class));
        this.queryContextRetriever = queryContextRetriever;
        this.retrievalAgent = retrievalAgent;
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
    protected boolean shouldTerminate(ModelResponse response) {
        return !response.needsAdditionalData();
    }

    @Override
    protected String getNextMessage(ModelResponse response) {
        if (response.dataQuery() == null || response.dataQuery().isBlank()) {
            return "Your previous response says additional data is needed, but dataQuery is missing. Provide a specific dataQuery.";
        }

        String retrievalResult = retrievalAgent.execute(response.dataQuery());
        return "You requested additional data with the following query:\n" + response.dataQuery()
                + "\nThe retrieved data is:\n" + retrievalResult;
    }

    @Override
    protected String convertModelResponse(ModelResponse response) {
        if (response.answer() != null && !response.answer().isBlank()) {
            return response.answer();
        }
        return response.analysis() != null ? response.analysis() : "No answer generated.";
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
