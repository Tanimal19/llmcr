package com.llmcr.rag.retrieval.select;

import java.util.List;

import com.llmcr.rag.retrieval.QueryContextRetriever.ContextScorePair;

public class FixedKStrategy implements TopKSelectionStrategy {
    public List<ContextScorePair> select(List<ContextScorePair> context, int topK) {
        return context.stream().limit(topK).toList();
    }
}
