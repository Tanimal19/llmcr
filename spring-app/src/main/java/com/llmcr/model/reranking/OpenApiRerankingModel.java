package com.llmcr.model.reranking;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OpenApiRerankingModel implements RerankingModel {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final String defaultModel;

    public OpenApiRerankingModel(
            WebClient.Builder builder,
            @Value("${llmcr.reranking.url}") String baseUrl,
            @Value("${llmcr.reranking.model}") String defaultModel) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.defaultModel = defaultModel;
    }

    @Override
    public RerankingResponse rerank(RerankingRequest request) {
        Assert.notNull(request, "request must not be null");
        Assert.hasText(request.getModel(), "model must not be blank");
        Assert.hasText(request.getQuery(), "query must not be blank");
        Assert.notEmpty(request.getDocuments(), "documents must not be empty");

        OpenApiRerankingApiResponse apiResponse = this.webClient.post()
                .uri("/v1/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new OpenApiRerankingApiRequest(
                        request.getModel(),
                        request.getQuery(),
                        request.getDocuments()))
                .retrieve()
                .bodyToMono(OpenApiRerankingApiResponse.class)
                .timeout(DEFAULT_TIMEOUT)
                .block();

        if (apiResponse == null || apiResponse.results() == null || apiResponse.results().isEmpty()) {
            return new RerankingResponse(List.of());
        }

        List<RerankingResponse.RerankingResult> results = apiResponse.results().stream()
                .map(result -> new RerankingResponse.RerankingResult(
                        new RerankingResponse.RankedDocument(
                                result.index(),
                                result.relevanceScore(),
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

    private record OpenApiRerankingApiRequest(String model, String query, List<String> documents) {
    }

    private record OpenApiRerankingApiResponse(String model, String object, OpenApiUsage usage,
            List<OpenApiResult> results) {
    }

    private record OpenApiUsage(int prompt_tokens, int total_tokens) {
    }

    private record OpenApiResult(int index, double relevance_score) {

        private double relevanceScore() {
            return relevance_score;
        }
    }

}
