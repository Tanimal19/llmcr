package com.llmcr.service.vectorstore;

import java.util.List;

import org.springframework.ai.vectorstore.SearchRequest;

import com.llmcr.entity.Chunk;

public abstract class MyVectorStore {

    /**
     * Add chunks to a collection.
     */
    public abstract void add(List<Chunk> chunks, String collectionName);

    public record ChunkWithScore(Long chunkId, float score) {
    }

    /**
     * Search chunks within a collection. Return a list of chunk id and score pairs,
     * sorted by score in descending order.
     */
    public List<ChunkWithScore> similaritySearch(SearchRequest request, String collectionName) {
        return doSimilaritySearch(request, collectionName).stream()
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .toList();
    }

    public abstract List<ChunkWithScore> doSimilaritySearch(SearchRequest request, String collectionName);
}
