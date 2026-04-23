package com.llmcr.service.vectorstore;

import java.util.List;

import com.llmcr.entity.Chunk;
import com.llmcr.service.rag.retrieval.ContextRetriever.ChunkScorePair;

public abstract class MyVectorStore {

    /**
     * Add chunks to a collection.
     */
    public abstract void add(List<Chunk> chunks, String collectionName);

    /**
     * Search chunks within a collection. Return a list of chunk id and score pairs,
     * sorted by score in descending order.
     */
    public List<ChunkScorePair> similaritySearch(String query, int topK, String collectionName) {
        return doSimilaritySearch(query, topK, collectionName).stream()
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .toList();
    }

    protected abstract List<ChunkScorePair> doSimilaritySearch(String query, int topK, String collectionName);
}
