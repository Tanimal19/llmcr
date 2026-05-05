package com.llmcr.service.rag.retrieval.select;

import java.util.List;

import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextScorePair;

public class FixedKStrategy implements TopKSelectionStrategy {
    public List<ContextScorePair> select(List<ContextScorePair> context, int topK) {
        return context.stream().limit(topK).toList();
    }
}
