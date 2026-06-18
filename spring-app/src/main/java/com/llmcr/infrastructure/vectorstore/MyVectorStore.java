package com.llmcr.infrastructure.vectorstore;

import com.llmcr.domain.entity.Context;
import com.llmcr.domain.repository.ContextRepository;
import com.llmcr.infrastructure.rag.ContextScorePair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract vector store for storing and searching Context embeddings. Operates on a single shared
 * FAISS index; callers filter by contextIds rather than named collections.
 */
public abstract class MyVectorStore {

  protected final ContextRepository contextRepository;

  protected MyVectorStore(ContextRepository contextRepository) {
    this.contextRepository = contextRepository;
  }

  /** Embed any un-embedded chunks from the given contexts and add them to the FAISS index. */
  public abstract void addContexts(List<Context> contexts);

  /**
   * Search the FAISS index and return contexts ranked by relevance.
   *
   * @param contextIds if non-null and non-empty, only chunks belonging to these contexts are
   *     considered; null means search all.
   */
  public List<ContextScorePair> search(String query, int topK, Set<Long> contextIds) {
    List<Long> allowedChunkIds = null;
    if (contextIds != null && !contextIds.isEmpty()) {
      allowedChunkIds = contextRepository.findChunkIdsByContextIds(new ArrayList<>(contextIds));
      if (allowedChunkIds.isEmpty()) {
        return List.of();
      }
    }

    List<ChunkIdScorePair> chunkResults = doSearch(query, topK, allowedChunkIds);
    return mergeToContexts(chunkResults);
  }

  /** Perform the vector search; allowedChunkIds=null means search the entire index. */
  protected abstract List<ChunkIdScorePair> doSearch(
      String query, int topK, List<Long> allowedChunkIds);

  /** Remove all chunks belonging to the given context IDs from the FAISS index. */
  public abstract void removeContexts(List<Long> contextIds);

  /** Clear the entire FAISS index. */
  public abstract void clearAll();

  private List<ContextScorePair> mergeToContexts(List<ChunkIdScorePair> chunks) {
    if (chunks.isEmpty()) {
      return List.of();
    }

    Map<Long, Float> chunkScores =
        chunks.stream()
            .collect(Collectors.toMap(ChunkIdScorePair::chunkId, ChunkIdScorePair::score));

    // Single query: chunk_id → context_id
    List<Long> chunkIds = new ArrayList<>(chunkScores.keySet());
    List<Object[]> pairs = contextRepository.findChunkContextIdPairs(chunkIds);

    Map<Long, Float> contextMaxScore = new HashMap<>();
    for (Object[] row : pairs) {
      Long chunkId = (Long) row[0];
      Long contextId = (Long) row[1];
      float score = chunkScores.getOrDefault(chunkId, 0f);
      contextMaxScore.merge(contextId, score, Math::max);
    }

    List<Context> contexts =
        contextRepository.findAllById(new ArrayList<>(contextMaxScore.keySet()));

    return contexts.stream()
        .map(c -> new ContextScorePair(c, contextMaxScore.get(c.getId())))
        .sorted((a, b) -> Float.compare(b.score(), a.score()))
        .toList();
  }
}
