package com.llmcr.service.rag.retrieval.rerank;

import java.util.List;

import com.llmcr.service.rag.retrieval.ContextRetriever.ChunkScorePair;

/**
 * A simple reranking strategy that always selects the topK chunks without any
 * modification.
 */
public class FixedKStrategy implements RerankStrategy {
    public List<ChunkScorePair> rerank(List<ChunkScorePair> chunks, int topK) {
        return chunks.stream().limit(topK).toList();
    }
}
