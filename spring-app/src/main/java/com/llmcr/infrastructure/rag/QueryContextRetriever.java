package com.llmcr.infrastructure.rag;

import com.llmcr.config.provider.RerankingModelConfigProvider;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import com.llmcr.infrastructure.ai.reranking.RerankingModel;
import com.llmcr.infrastructure.ai.reranking.RerankingResponse;
import com.llmcr.infrastructure.rag.fusion.FusionStrategy;
import com.llmcr.infrastructure.rag.fusion.RankFusionStrategy;
import com.llmcr.infrastructure.rag.select.AdaptiveKStrategy;
import com.llmcr.infrastructure.rag.select.TopKSelectionStrategy;
import com.llmcr.infrastructure.vectorstore.MyVectorStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Retrieve relevant contexts for the query. The retrieval process: 1) Search the vector store with
 * an optional context filter. 2) Optionally rerank the results. 3) Select topK contexts.
 */
@Component
public class QueryContextRetriever {

  public record QueryContextRetrievalRequest(
      List<String> queries, QueryContextRetrievalConfig config) {}

  /**
   * @param contextIds if non-null and non-empty, only these contexts are searched; null = all.
   * @param topK number of contexts to return.
   * @param topKSelectionStrategy strategy to select topK from ranked results.
   * @param fusionStrategy strategy to fuse multiple query results.
   * @param useReranker whether to apply the reranking model.
   */
  public record QueryContextRetrievalConfig(
      Set<Long> contextIds,
      int topK,
      TopKSelectionStrategy topKSelectionStrategy,
      FusionStrategy fusionStrategy,
      boolean useReranker) {

    public QueryContextRetrievalConfig(Set<Long> contextIds, int topK) {
      this(contextIds, topK, new AdaptiveKStrategy(), new RankFusionStrategy(), false);
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(QueryContextRetriever.class);

  private static final int TOP_N = 1000;
  private static final int MAX_QUERY_LENGTH = 7200;

  private final MyVectorStore vectorStore;
  private final RerankingModel rerankingModel;

  public QueryContextRetriever(
      RerankingModelConfigProvider rerankingModelConfigProvider,
      MyVectorStore vectorStore,
      ModelClientFactory modelClientFactory) {
    this.vectorStore = vectorStore;
    this.rerankingModel =
        modelClientFactory.createRerankingModel(
            rerankingModelConfigProvider.getRerankingModelConfig());
  }

  public List<ContextScorePair> retrieve(QueryContextRetrievalRequest request) {
    if (request == null || request.config() == null) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.INVALID_REQUEST,
          "Context retrieval request/config cannot be null");
    }

    if (request.queries() == null || request.queries().isEmpty()) {
      return List.of();
    }

    try {
      List<String> processedQueries = new ArrayList<>();
      for (String query : request.queries()) {
        if (query == null || query.isEmpty()) {
          continue;
        }
        if (query.length() <= MAX_QUERY_LENGTH) {
          processedQueries.add(query);
        } else {
          processedQueries.addAll(splitQuery(query));
        }
      }
      logger.debug("Split queries: {} -> {}", request.queries().size(), processedQueries.size());

      if (processedQueries.isEmpty()) {
        return List.of();
      }

      List<ContextScorePair> retrievedContexts;
      if (processedQueries.size() == 1) {
        retrievedContexts = retrieveSingleQuery(processedQueries.get(0), request.config());
      } else {
        retrievedContexts = retrieveMultiQuery(processedQueries, request.config());
      }

      logger.info(
          "Retrieved contexts: {}",
          retrievedContexts.stream()
              .map(
                  c ->
                      String.format(
                          "\n[Context: %s (id=%d), Score: %.4f]",
                          c.context().getName(), c.context().getId(), c.score()))
              .toList());

      return retrievedContexts;
    } catch (APIServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.RAG_RETRIEVAL_FAILED, "Failed to retrieve contexts", ex);
    }
  }

  private List<ContextScorePair> retrieveSingleQuery(
      String query, QueryContextRetrievalConfig config) {
    if (query == null || query.isBlank()) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.INVALID_REQUEST, "Query cannot be null or blank");
    }
    if (config == null) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.INVALID_REQUEST, "Retrieval config cannot be null");
    }
    if (config.topK() < 0) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.INVALID_REQUEST, "topK cannot be negative");
    }
    if (config.topKSelectionStrategy() == null) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.INVALID_REQUEST, "topK selection strategy cannot be null");
    }

    logger.info(
        "Retrieving contexts for query: '{}'", query.substring(0, Math.min(100, query.length())));

    List<ContextScorePair> topNContexts;
    try {
      topNContexts = vectorStore.search(query, TOP_N, config.contextIds());
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.RAG_VECTOR_SEARCH_FAILED, "Vector search failed", ex);
    }

    if (topNContexts == null || topNContexts.isEmpty()) {
      return List.of();
    }

    List<ContextScorePair> rankedContexts;
    if (config.useReranker()) {
      rankedContexts = rerank(query, topNContexts);
    } else {
      rankedContexts = topNContexts;
    }

    if (rankedContexts.size() <= config.topK()) {
      return rankedContexts;
    }

    try {
      return config.topKSelectionStrategy().select(rankedContexts, config.topK());
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.RAG_TOPK_SELECTION_FAILED,
          "Failed to select top-k contexts",
          ex);
    }
  }

  private List<ContextScorePair> retrieveMultiQuery(
      List<String> queries, QueryContextRetrievalConfig config) {
    try {
      List<List<ContextScorePair>> contextLists =
          queries.stream().map(q -> retrieveSingleQuery(q, config)).toList();

      return config.fusionStrategy().fuse(contextLists, config.topK());
    } catch (APIServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.RAG_FUSION_FAILED,
          "Failed to fuse multi-query retrieval results",
          ex);
    }
  }

  private List<String> splitQuery(String query) {
    List<String> segments = new ArrayList<>();
    int start = 0;
    while (start < query.length()) {
      int end = Math.min(start + MAX_QUERY_LENGTH, query.length());
      segments.add(query.substring(start, end));
      start = end;
    }
    return segments;
  }

  private List<ContextScorePair> rerank(String query, List<ContextScorePair> contexts) {
    try {
      List<String> documents =
          contexts.stream()
              .map(p -> p.context().getContent() == null ? "" : p.context().getContent())
              .toList();

      RerankingResponse rerankingResponse = rerankingModel.rerank(query, documents);
      if (rerankingResponse == null || rerankingResponse.getResults() == null) {
        return List.of();
      }

      List<ContextScorePair> rankedContexts = new ArrayList<>();
      for (RerankingResponse.RerankingResult result : rerankingResponse.getResults()) {
        if (result == null || result.getOutput() == null) {
          continue;
        }
        int index = result.getOutput().index();
        if (index < 0 || index >= contexts.size()) {
          continue;
        }
        float score = (float) result.getOutput().relevanceScore();
        rankedContexts.add(new ContextScorePair(contexts.get(index).context(), score));
      }

      return rankedContexts;
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.RAG_RERANK_FAILED, "Reranking failed", ex);
    }
  }
}
