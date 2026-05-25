package com.llmcr.service.rag.select;

import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import java.util.List;

public class FixedKStrategy implements TopKSelectionStrategy {

    public List<ContextScorePair> select(List<ContextScorePair> context, int topK) {
        return context.stream().limit(topK).toList();
    }
}
