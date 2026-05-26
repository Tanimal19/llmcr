package com.llmcr.rag.fusion;

import java.util.List;

import com.llmcr.rag.QueryContextRetriever.ContextScorePair;

public interface FusionStrategy {
    public List<ContextScorePair> fuse(List<List<ContextScorePair>> contextLists, int topK);
}
