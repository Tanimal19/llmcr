package com.llmcr.rag.retrieval;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.client.RerankingClient;
import com.llmcr.client.reranking.RerankingResponse;
import com.llmcr.entity.Context;
import com.llmcr.rag.retrieval.fusion.FusionStrategy;
import com.llmcr.rag.retrieval.fusion.RankFusionStrategy;
import com.llmcr.rag.retrieval.select.TopKSelectionStrategy;
import com.llmcr.repository.ContextRepository;
import com.llmcr.vectorstore.MyVectorStore;

/**
 * Retrieve relevant contexts for the query. The retrieval process includes
 * these steps:
 * 1) Retrieve topN relevant chunks from the vector store.
 * 2) Merge the chunks into contexts
 * 3) Rerank the contexts and select topK contexts based on the specified
 * select strategy.
 */
@Component
public class QueryContextRetriever {

    /**
     * @param queries the list of queries to retrieve contexts for. If
     *                there are multiple queries, the retrieval will
     *                be performed for each query and the results will
     *                be fused using the RRF fusion strategy.
     */
    public record ContextRetrievalRequest(List<String> queries, ContextRetrievalConfiguration config) {
    }

    /**
     * @param topK                  the number of contexts to return after retrieval
     *                              and
     *                              reranking.
     * @param topKSelectionStrategy the strategy to select topK contexts from the
     *                              ranked
     *                              contexts.
     * @param collectionName        the name of the vector store collection to
     *                              search for
     *                              relevant chunks.
     * @param useReranker           whether to use the reranking model to rerank the
     *                              retrieved contexts.
     */
    public record ContextRetrievalConfiguration(int topK, TopKSelectionStrategy topKSelectionStrategy,
            String collectionName, boolean useReranker) {
    }

    public record ChunkIdScorePair(Long chunkId, float score) {
    }

    public record ContextScorePair(Context context, float score) {
    }

    private static final Logger logger = LoggerFactory.getLogger(QueryContextRetriever.class);

    private static final int MAX_QUERY_LENGTH = 4096;
    private static final int TOP_N = 1000;
    private final MyVectorStore vectorStore;
    private final ContextRepository contextRepository;
    private final RerankingClient rerankingModel;

    public QueryContextRetriever(MyVectorStore vectorStore, ContextRepository contextRepository,
            RerankingClient rerankingModel) {
        this.vectorStore = vectorStore;
        this.contextRepository = contextRepository;
        this.rerankingModel = rerankingModel;
    }

    public List<ContextScorePair> retrieve(ContextRetrievalRequest request) {
        if (request.queries() == null || request.queries().isEmpty()) {
            return List.of();
        }

        // Make sure each query is not null or empty, and split long queries into
        // segments
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

        List<ContextScorePair> retrievedContexts;
        if (processedQueries.size() == 1) {
            String query = processedQueries.get(0);
            retrievedContexts = retrieveSingleQuery(query, request.config());
        } else {
            retrievedContexts = retrieveMultiQuery(processedQueries, request.config(), new RankFusionStrategy());
        }

        logger.info("Retrieved contexts: {}", retrievedContexts.stream()
                .map(c -> String.format("\n[Context: %s (id=%d), Score: %.4f]", c.context().getName(),
                        c.context().getId(), c.score()))
                .toList());

        return retrievedContexts;
    }

    private List<ContextScorePair> retrieveSingleQuery(String query, ContextRetrievalConfiguration config) {
        logger.info("Retrieving contexts for query: {}",
                query.substring(0, Math.min(100, query.length())) + (query.length() > 100 ? "..." : ""));

        List<ChunkIdScorePair> topNChunks = vectorStore.similaritySearch(query, TOP_N, config.collectionName());

        List<ContextScorePair> rankedContexts;
        if (config.useReranker()) {
            rankedContexts = rerank(query, topNChunks);
        } else {
            rankedContexts = merge(query, topNChunks);
        }

        if (rankedContexts.size() <= config.topK()) {
            return rankedContexts;
        }

        return config.topKSelectionStrategy.select(rankedContexts, config.topK());
    }

    private List<ContextScorePair> retrieveMultiQuery(List<String> queries, ContextRetrievalConfiguration config,
            FusionStrategy fusionStrategy) {
        List<List<ContextScorePair>> contextLists = queries.stream()
                .map(q -> retrieveSingleQuery(q, config))
                .toList();

        return fusionStrategy.fuse(contextLists, config.topK());
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

    private List<ContextScorePair> rerank(String query, List<ChunkIdScorePair> chunks) {
        List<Context> contexts = contextRepository.findAllByChunkIds(chunks.stream()
                .map(ChunkIdScorePair::chunkId)
                .toList());

        List<String> documents = contexts.stream()
                .map(c -> c.getContent() == null ? "" : c.getContent())
                .toList();

        RerankingResponse rerankingResponse = rerankingModel.rerank(query, documents);
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
            rankedContexts.add(new ContextScorePair(contexts.get(index), score));
        }

        return rankedContexts;
    }

    private List<ContextScorePair> merge(String query, List<ChunkIdScorePair> chunks) {
        // This method is not used in the current implementation, but can be used to
        // merge chunks into contexts and assign a score to each context based on the
        // chunk scores.
        Map<Context, Float> contextScoreMap = new HashMap<>();
        chunks.stream().forEach(c -> {
            Context context = contextRepository.findByChunkId(c.chunkId());

            Float existingScore = contextScoreMap.getOrDefault(context, 0.0f);
            contextScoreMap.put(context, Math.max(existingScore, c.score()));
        });

        return contextScoreMap.entrySet().stream()
                .map(e -> new ContextScorePair(e.getKey(), e.getValue()))
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .toList();
    }
}