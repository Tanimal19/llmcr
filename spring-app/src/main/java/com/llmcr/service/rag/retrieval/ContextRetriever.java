package com.llmcr.service.rag.retrieval;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.model.RerankingClient;
import com.llmcr.model.reranking.RerankingResponse;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.rag.retrieval.fusion.FusionStrategy;
import com.llmcr.service.rag.retrieval.select.SelectStrategy;
import com.llmcr.vectorstore.MyVectorStore;

@Component
public class ContextRetriever {

    private static final int topN = 1000;
    private final MyVectorStore vectorStore;
    private final ContextRepository contextRepository;
    private final RerankingClient rerankingModel;

    public record RetrievalConfiguration(int topK, String collectionName, boolean useReranker,
            SelectStrategy selectStrategy) {
    }

    public record ChunkScorePair(Long chunkId, float score) {
    }

    public record ContextScorePair(Context context, float score) {
    }

    public ContextRetriever(MyVectorStore vectorStore, ContextRepository contextRepository,
            RerankingClient rerankingModel) {
        this.vectorStore = vectorStore;
        this.contextRepository = contextRepository;
        this.rerankingModel = rerankingModel;
    }

    /**
     * Retrieve relevant contexts for the query. The retrieval process includes
     * these steps:
     * 1) Retrieve topN relevant chunks from the vector store.
     * 2) Merge the chunks into contexts
     * 3) Rerank the contexts and select topK contexts based on the specified
     * select strategy.
     */
    public List<ContextScorePair> retrieve(String query, RetrievalConfiguration config) {
        List<ChunkScorePair> topNChunks = vectorStore.similaritySearch(query, topN, config.collectionName());

        List<ContextScorePair> rankedContexts;
        if (config.useReranker()) {
            rankedContexts = rerank(query, topNChunks);
        } else {
            rankedContexts = merge(query, topNChunks);
        }

        if (rankedContexts.size() <= config.topK()) {
            return rankedContexts;
        }

        return config.selectStrategy.select(rankedContexts, config.topK());
    }

    public List<Context> retrieve(List<String> queries, RetrievalConfiguration config, FusionStrategy fusionStrategy) {
        List<List<ContextScorePair>> contextLists = queries.stream()
                .map(q -> retrieve(q, config))
                .toList();

        return fusionStrategy.fuse(contextLists, config.topK()).stream()
                .map(ContextScorePair::context)
                .toList();
    }

    private List<ContextScorePair> rerank(String query, List<ChunkScorePair> chunks) {
        List<Context> contexts = contextRepository.findAllByChunkIds(chunks.stream()
                .map(ChunkScorePair::chunkId)
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

    private List<ContextScorePair> merge(String query, List<ChunkScorePair> chunks) {
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