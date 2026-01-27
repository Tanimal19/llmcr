package com.example.llmcr.service.faiss;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class FaissService {

    private final WebClient webClient;

    @Autowired
    public FaissService(
            WebClient.Builder builder,
            @Value("${faiss.service.url}") String baseUrl) {

        this.webClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Add vectors to the FAISS index
     */
    public AddVectorsResponse addVectors(AddVectorsRequest request) {
        return webClient.post()
                .uri("/index/add")
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
                .uri("/index/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SearchVectorsResponse.class)
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
}
