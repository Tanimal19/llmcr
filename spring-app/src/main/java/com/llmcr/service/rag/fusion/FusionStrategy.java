package com.llmcr.service.rag.fusion;

import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import java.util.List;

public interface FusionStrategy {
    public List<ContextScorePair> fuse(List<List<ContextScorePair>> contextLists, int topK);
}
