package com.llmcr.infrastructure.rag.select;

import java.util.List;

import com.llmcr.infrastructure.rag.ContextScorePair;

public class FixedKStrategy implements TopKSelectionStrategy {

    public List<ContextScorePair> select(List<ContextScorePair> context, int topK) {
        return context.stream().limit(topK).toList();
    }
}
