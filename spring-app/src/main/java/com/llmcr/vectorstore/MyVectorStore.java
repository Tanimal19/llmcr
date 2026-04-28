package com.llmcr.vectorstore;

import java.util.List;

import com.llmcr.entity.Chunk;
import com.llmcr.service.rag.retrieval.ContextRetriever.ChunkIdScorePair;

public abstract class MyVectorStore {

    /**
     * Add chunks to a collection.
     */
    public abstract void addChunks(List<Chunk> chunks, String collectionName);

    /**
     * Search chunks within a collection. Return a list of chunk id and score pairs,
     * sorted by score in descending order.
     */
    public List<ChunkIdScorePair> similaritySearch(String query, int topK, String collectionName) {
        return doSimilaritySearch(query, topK, collectionName).stream()
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .toList();
    }

    protected abstract List<ChunkIdScorePair> doSimilaritySearch(String query, int topK, String collectionName);

    public abstract void removeCollection(String collectionName);

    public abstract void removeChunks(List<Long> chunkId, String collectionName);
}
