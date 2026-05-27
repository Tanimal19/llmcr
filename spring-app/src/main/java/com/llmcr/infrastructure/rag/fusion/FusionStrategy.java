package com.llmcr.infrastructure.rag.fusion;

import com.llmcr.infrastructure.rag.ContextScorePair;
import java.util.List;

public interface FusionStrategy {
  public List<ContextScorePair> fuse(List<List<ContextScorePair>> contextLists, int topK);
}
