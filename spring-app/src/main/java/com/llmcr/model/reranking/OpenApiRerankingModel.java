package com.llmcr.model.reranking;

import java.util.List;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

public class OpenApiRerankingModel implements RerankingModel {

    private final OpenAiRerankingApi openAiRerankingApi;
    private final String defaultModel;
    private final RetryTemplate retryTemplate;

    public OpenApiRerankingModel(OpenAiRerankingApi openAiRerankingApi) {
        this(openAiRerankingApi, "");
    }

    public OpenApiRerankingModel(OpenAiRerankingApi openAiRerankingApi, String defaultModel) {
        this(openAiRerankingApi, defaultModel, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    public OpenApiRerankingModel(OpenAiRerankingApi openAiRerankingApi, String defaultModel,
            RetryTemplate retryTemplate) {
        Assert.notNull(openAiRerankingApi, "openAiRerankingApi must not be null");
        Assert.notNull(retryTemplate, "retryTemplate must not be null");
        this.openAiRerankingApi = openAiRerankingApi;
        this.defaultModel = defaultModel;
        this.retryTemplate = retryTemplate;
    }

    @Override
    public RerankingResponse rerank(RerankingRequest request) {
        Assert.notNull(request, "request must not be null");
        Assert.hasText(request.getModel(), "model must not be blank");
        Assert.hasText(request.getQuery(), "query must not be blank");
        Assert.notEmpty(request.getDocuments(), "documents must not be empty");

        OpenAiRerankingApi.RerankingApiResponse apiResponse = this.retryTemplate.execute(
                ctx -> this.openAiRerankingApi.rerank(new OpenAiRerankingApi.RerankingApiRequest(
                        request.getModel(),
                        request.getQuery(),
                        request.getDocuments())));

        if (apiResponse == null || apiResponse.results() == null || apiResponse.results().isEmpty()) {
            return new RerankingResponse(List.of());
        }

        List<RerankingResponse.RerankingResult> results = apiResponse.results().stream()
                .map(result -> new RerankingResponse.RerankingResult(
                        new RerankingResponse.RankedDocument(
                                result.index(),
                                result.relevance_score(),
                                getDocumentSafely(request.getDocuments(), result.index()))))
                .toList();

        return new RerankingResponse(results);
    }

    @Override
    public RerankingResponse rerank(String query, List<String> documents) {
        Assert.hasText(this.defaultModel, "default model must not be blank");
        return rerank(new RerankingRequest(this.defaultModel, query, documents));
    }

    private static String getDocumentSafely(List<String> documents, int index) {
        if (index < 0 || index >= documents.size()) {
            return null;
        }
        return documents.get(index);
    }

}
