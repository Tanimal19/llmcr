package com.llmcr.infrastructure.rag.fusion;

import java.util.List;

import com.llmcr.infrastructure.rag.ContextScorePair;

public interface FusionStrategy {
    public List<ContextScorePair> fuse(List<List<ContextScorePair>> contextLists, int topK);
}
