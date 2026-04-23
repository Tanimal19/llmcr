package com.llmcr.service.rag.retrieval.select;

import java.util.List;

import com.llmcr.service.rag.retrieval.ContextRetriever.ContextScorePair;

public class FixedKStrategy implements SelectStrategy {
    public List<ContextScorePair> select(List<ContextScorePair> context, int topK) {
        return context.stream().limit(topK).toList();
    }
}
