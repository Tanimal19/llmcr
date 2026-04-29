package com.llmcr.service.rag.select;

import java.util.List;

import com.llmcr.service.rag.ContextRetriever.ContextScorePair;

public interface TopKSelectionStrategy {
    /**
     * Selects the top K contexts based on the given strategy. The input list is
     * assumed to be pre-sorted by relevance score in descending order.
     */
    public List<ContextScorePair> select(List<ContextScorePair> contexts, int topK);
}
