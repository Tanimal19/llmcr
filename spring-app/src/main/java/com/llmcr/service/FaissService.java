package com.llmcr.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class FaissService {

    private final WebClient webClient;

    public FaissService(
            WebClient.Builder builder,
            @Value("${llmcr.faiss.url}") String baseUrl) {

        this.webClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Add vectors to the FAISS index
     */
    public AddVectorsResponse addVectors(AddVectorsRequest request) {
        return webClient.post()
                .uri("/index/add_ids")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AddVectorsResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    /**
     * Search for similar vectors in the FAISS index
     */
    public SearchVectorsResponse searchVectors(SearchVectorsRequest request) {
        return webClient.post()
                .uri("/index/search_ids")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SearchVectorsResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public void removeIndex(RemoveIndexRequest request) {
        webClient.post()
                .uri("/index/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public RemoveVectorsResponse removeVectors(RemoveVectorsRequest request) {
        return webClient.post()
                .uri("/index/remove_ids")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RemoveVectorsResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Request/Response DTOs
    public record AddVectorsRequest(
            String index_name,
            List<Long> ids,
            List<float[]> vectors) {
    }

    public record AddVectorsResponse(
            String status,
            int added_count) {
    }

    public record SearchVectorsRequest(
            String index_name,
            float[] qvector,
            int top_k) {
    }

    public record SearchVectorsResponse(
            List<Long> ids,
            List<Float> scores) {
    }

    public record RemoveIndexRequest(
            String index_name) {
    }

    public record RemoveVectorsRequest(
            String index_name,
            List<Long> ids) {
    }

    public record RemoveVectorsResponse(
            String status,
            int removed_count) {
    }
}
