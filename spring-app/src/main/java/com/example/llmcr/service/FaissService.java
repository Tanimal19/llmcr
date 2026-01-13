package com.example.llmcr.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Service
public class FaissService {

    @Value("${faiss.service.url}")
    private String faissServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Add vectors to the FAISS index
     */
    public AddVectorsResponse addVectors(List<Integer> ids, List<List<Float>> vectors) {
        String url = faissServiceUrl + "/index/add";

        AddVectorsRequest request = new AddVectorsRequest(ids, vectors);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AddVectorsRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<AddVectorsResponse> response = restTemplate.postForEntity(
                url, entity, AddVectorsResponse.class);

        return response.getBody();
    }

    /**
     * Search for similar vectors in the FAISS index
     */
    public SearchResponse search(List<Float> queryVector, int topK) {
        String url = faissServiceUrl + "/index/search";

        SearchRequest request = new SearchRequest(queryVector, topK);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SearchRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<SearchResponse> response = restTemplate.postForEntity(
                url, entity, SearchResponse.class);

        return response.getBody();
    }

    // Request/Response DTOs
    public static class AddVectorsRequest {
        private List<Integer> ids;
        private List<List<Float>> vectors;

        public AddVectorsRequest() {
        }

        public AddVectorsRequest(List<Integer> ids, List<List<Float>> vectors) {
            this.ids = ids;
            this.vectors = vectors;
        }

        public List<Integer> getIds() {
            return ids;
        }

        public void setIds(List<Integer> ids) {
            this.ids = ids;
        }

        public List<List<Float>> getVectors() {
            return vectors;
        }

        public void setVectors(List<List<Float>> vectors) {
            this.vectors = vectors;
        }
    }

    public static class AddVectorsResponse {
        private String status;
        private int added_count;

        public AddVectorsResponse() {
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getAdded_count() {
            return added_count;
        }

        public void setAdded_count(int added_count) {
            this.added_count = added_count;
        }
    }

    public static class SearchRequest {
        private List<Float> qvector;
        private int top_k;

        public SearchRequest() {
        }

        public SearchRequest(List<Float> qvector, int top_k) {
            this.qvector = qvector;
            this.top_k = top_k;
        }

        public List<Float> getQvector() {
            return qvector;
        }

        public void setQvector(List<Float> qvector) {
            this.qvector = qvector;
        }

        public int getTop_k() {
            return top_k;
        }

        public void setTop_k(int top_k) {
            this.top_k = top_k;
        }
    }

    public static class SearchResponse {
        private List<Integer> ids;
        private List<Float> scores;

        public SearchResponse() {
        }

        public List<Integer> getIds() {
            return ids;
        }

        public void setIds(List<Integer> ids) {
            this.ids = ids;
        }

        public List<Float> getScores() {
            return scores;
        }

        public void setScores(List<Float> scores) {
            this.scores = scores;
        }
    }
}
