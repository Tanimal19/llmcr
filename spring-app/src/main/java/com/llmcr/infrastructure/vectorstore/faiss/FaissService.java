package com.llmcr.infrastructure.vectorstore.faiss;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class FaissService {

    private final WebClient webClient;

    public FaissService(WebClient.Builder builder, @Value("${llmcr.faiss.url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * Add vectors to the FAISS index
     */
    public AddVectorsResponse addVectors(AddVectorsRequest request) {
        return webClient
                .post()
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
        return webClient
                .post()
                .uri("/index/search_ids")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SearchVectorsResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public void removeIndex(RemoveIndexRequest request) {
        webClient
                .post()
                .uri("/index/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public RemoveVectorsResponse removeVectors(RemoveVectorsRequest request) {
        return webClient
                .post()
                .uri("/index/remove_ids")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RemoveVectorsResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Request/Response DTOs
    public record AddVectorsRequest(String index_name, List<Long> ids, List<float[]> vectors) {
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof AddVectorsRequest other))
                return false;
            return Objects.equals(index_name, other.index_name)
                    && Objects.equals(ids, other.ids)
                    && vectorsEqual(vectors, other.vectors);
        }

        private static boolean vectorsEqual(List<float[]> a, List<float[]> b) {
            if (a == b)
                return true;
            if (a == null || b == null || a.size() != b.size())
                return false;
            for (int i = 0; i < a.size(); i++) {
                if (!Arrays.equals(a.get(i), b.get(i)))
                    return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(index_name, ids);
            if (vectors != null) {
                for (float[] v : vectors)
                    result = 31 * result + Arrays.hashCode(v);
            }
            return result;
        }

        @Override
        public String toString() {
            List<String> vStrs = vectors == null ? null
                    : vectors.stream().map(Arrays::toString).toList();
            return "AddVectorsRequest[index_name=" + index_name
                    + ", ids=" + ids
                    + ", vectors=" + vStrs + "]";
        }
    }

    public record AddVectorsResponse(String status, int added_count) {
    }

    public record SearchVectorsRequest(String index_name, float[] qvector, int top_k) {
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof SearchVectorsRequest other))
                return false;
            return top_k == other.top_k
                    && Objects.equals(index_name, other.index_name)
                    && Arrays.equals(qvector, other.qvector);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index_name, Arrays.hashCode(qvector), top_k);
        }

        @Override
        public String toString() {
            return "SearchVectorsRequest[index_name=" + index_name
                    + ", qvector=" + Arrays.toString(qvector)
                    + ", top_k=" + top_k + "]";
        }
    }

    public record SearchVectorsResponse(List<Long> ids, List<Float> scores) {
    }

    public record RemoveIndexRequest(String index_name) {
    }

    public record RemoveVectorsRequest(String index_name, List<Long> ids) {
    }

    public record RemoveVectorsResponse(String status, int removed_count) {
    }
}
