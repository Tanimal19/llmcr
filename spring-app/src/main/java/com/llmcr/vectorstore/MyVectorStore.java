package com.llmcr.vectorstore;

import java.util.List;

import com.llmcr.entity.Chunk;
import com.llmcr.rag.retrieval.QueryContextRetriever.ChunkIdScorePair;

/**
 * This abstract class defines the interface for a vector store that can be used
 * for storing and retrieving chunks of context based on similarity search.
 * We're not using Spring AI's built-in vector store because we want to avoid
 * conversions between `Chunk` and Spring AI's `Document` format. One can use
 * Adapter pattern to adapt this interface to Spring AI's vector store if
 * needed.
 */
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
