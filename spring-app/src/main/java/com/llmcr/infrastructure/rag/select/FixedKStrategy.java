package com.llmcr.infrastructure.rag.select;

import com.llmcr.infrastructure.rag.ContextScorePair;
import java.util.List;

public class FixedKStrategy implements TopKSelectionStrategy {

  public List<ContextScorePair> select(List<ContextScorePair> context, int topK) {
    return context.stream().limit(topK).toList();
  }
}
