package com.llmcr.service.rag.retrieval.select;

import java.util.List;

import com.llmcr.service.rag.retrieval.ContextRetriever.ContextScorePair;

public interface SelectStrategy {
    /**
     * Selects the top K contexts based on the given strategy. The input list is
     * assumed to be pre-sorted by relevance score in descending order.
     */
    public List<ContextScorePair> select(List<ContextScorePair> contexts, int topK);
}
