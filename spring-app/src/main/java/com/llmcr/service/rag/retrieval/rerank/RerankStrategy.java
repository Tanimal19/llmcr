package com.llmcr.service.rag.retrieval.rerank;

import java.util.List;

import com.llmcr.service.rag.retrieval.ContextRetriever.ChunkScorePair;

public interface RerankStrategy {
    public List<ChunkScorePair> rerank(List<ChunkScorePair> chunks, int topK);
}
