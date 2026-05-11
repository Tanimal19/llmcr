package com.llmcr.rag.retrieval.fusion;

import java.util.List;

import com.llmcr.rag.retrieval.QueryContextRetriever.ContextScorePair;

public interface FusionStrategy {
    public List<ContextScorePair> fuse(List<List<ContextScorePair>> contextLists, int topK);
}
