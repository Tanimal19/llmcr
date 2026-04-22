package com.llmcr.service.vectorstore;

import java.util.List;

import com.llmcr.entity.Chunk;

public abstract class MyVectorStore {

    /**
     * Add chunks to a collection.
     */
    public abstract void add(List<Chunk> chunks, String collectionName);

    public record SearchRequest(String query, int topK, String collectionName) {
    }

    public record SearchResponse(Long chunkId, float score) {
    }

    /**
     * Search chunks within a collection. Return a list of chunk id and score pairs,
     * sorted by score in descending order.
     */
    public List<SearchResponse> similaritySearch(SearchRequest request) {
        return doSimilaritySearch(request).stream()
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .toList();
    }

    public abstract List<SearchResponse> doSimilaritySearch(SearchRequest request);
}
