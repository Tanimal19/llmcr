package com.llmcr.model.reranking;

import java.time.Duration;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

public class OpenAiRerankingApi {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;

    public OpenAiRerankingApi(String baseUrl) {
        this(baseUrl, "");
    }

    public OpenAiRerankingApi(String baseUrl, String apiKey) {
        this(WebClient.builder(), baseUrl, apiKey);
    }

    public OpenAiRerankingApi(WebClient.Builder builder, String baseUrl, String apiKey) {
        Assert.hasText(baseUrl, "baseUrl must not be blank");
        Assert.notNull(apiKey, "apiKey must not be null");
        this.webClient = builder.baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public RerankingApiResponse rerank(RerankingApiRequest request) {
        Assert.notNull(request, "request must not be null");
        return this.webClient.post()
                .uri("/v1/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RerankingApiResponse.class)
                .timeout(DEFAULT_TIMEOUT)
                .block();
    }

    public record RerankingApiRequest(String model, String query, List<String> documents) {
    }

    public record RerankingApiResponse(String model, String object, Usage usage, List<Result> results) {

        public record Usage(int prompt_tokens, int total_tokens) {
        }

        public record Result(int index, double relevance_score) {
        }
    }

}
