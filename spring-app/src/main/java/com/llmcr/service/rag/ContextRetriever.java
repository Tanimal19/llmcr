package com.llmcr.service.rag;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.model.RerankingClient;
import com.llmcr.model.reranking.RerankingResponse;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.rag.fusion.FusionStrategy;
import com.llmcr.service.rag.fusion.RankFusionStrategy;
import com.llmcr.service.rag.select.TopKSelectionStrategy;
import com.llmcr.vectorstore.MyVectorStore;

@Component
public class ContextRetriever {

    private static final Logger log = LoggerFactory.getLogger(ContextRetriever.class);

    private static final int maxQueryLength = 512;
    private static final int topN = 1000;
    private final MyVectorStore vectorStore;
    private final ContextRepository contextRepository;
    private final RerankingClient rerankingModel;

    /**
     * @param topK                  the number of contexts to return after retrieval
     *                              and
     *                              reranking.
     * @param collectionName        the name of the vector store collection to
     *                              search for
     *                              relevant chunks.
     * @param useReranker           whether to use the reranking model to rerank the
     *                              retrieved contexts.
     * @param topKSelectionStrategy the strategy to select topK contexts from the
     *                              ranked
     *                              contexts.
     */
    public record RetrievalConfiguration(int topK, String collectionName, boolean useReranker,
            TopKSelectionStrategy topKSelectionStrategy) {
    }

    public record ChunkIdScorePair(Long chunkId, float score) {
    }

    public record ContextScorePair(Context context, float score) {
    }

    public ContextRetriever(MyVectorStore vectorStore, ContextRepository contextRepository,
            RerankingClient rerankingModel) {
        this.vectorStore = vectorStore;
        this.contextRepository = contextRepository;
        this.rerankingModel = rerankingModel;
    }

    public List<ContextScorePair> retrieve(String query, RetrievalConfiguration config) {
        if (query == null || query.isEmpty()) {
            return List.of();
        }

        if (query.length() <= maxQueryLength) {
            return retrieveSingleQuery(query, config);
        } else {
            // For long query, we can split it into multiple segments and perform retrieval
            // for each segment, then fuse the results.
            log.info("Query length {} exceeds max length {}, splitting into segments for retrieval",
                    query.length(), maxQueryLength);
            List<String> segments = splitQuery(query);
            return retrieveMultiQuery(segments, config, new RankFusionStrategy());
        }
    }

    public List<ContextScorePair> retrieve(List<String> queries, RetrievalConfiguration config) {
        return retrieveMultiQuery(queries, config, new RankFusionStrategy());
    }

    /**
     * Retrieve relevant contexts for the query. The retrieval process includes
     * these steps:
     * 1) Retrieve topN relevant chunks from the vector store.
     * 2) Merge the chunks into contexts
     * 3) Rerank the contexts and select topK contexts based on the specified
     * select strategy.
     */
    private List<ContextScorePair> retrieveSingleQuery(String query, RetrievalConfiguration config) {
        log.info("Retrieving contexts for query: {}", query);

        List<ChunkIdScorePair> topNChunks = vectorStore.similaritySearch(query, topN, config.collectionName());

        List<ContextScorePair> rankedContexts;
        if (config.useReranker()) {
            rankedContexts = rerank(query, topNChunks);
        } else {
            rankedContexts = merge(query, topNChunks);
        }

        log.info("Ranked contexts before selection: {}", rankedContexts.stream()
                .map(c -> String.format("ContextId: %d, Score: %.4f", c.context().getId(), c.score()))
                .toList());

        if (rankedContexts.size() <= config.topK()) {
            return rankedContexts;
        }

        return config.topKSelectionStrategy.select(rankedContexts, config.topK());
    }

    private List<ContextScorePair> retrieveMultiQuery(List<String> queries, RetrievalConfiguration config,
            FusionStrategy fusionStrategy) {
        List<List<ContextScorePair>> contextLists = queries.stream()
                .map(q -> retrieve(q, config))
                .toList();

        return fusionStrategy.fuse(contextLists, config.topK());
    }

    private List<String> splitQuery(String query) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < query.length()) {
            int end = Math.min(start + maxQueryLength, query.length());
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
        // merge
        // chunks into contexts and assign a score to each context based on the chunk
        // scores.
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