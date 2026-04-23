package com.llmcr.service.rag.retrieval;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.rag.retrieval.fusion.FusionStrategy;
import com.llmcr.service.rag.retrieval.select.SelectStrategy;
import com.llmcr.service.vectorstore.MyVectorStore;

@Component
public class ContextRetriever {

    private static final int topN = 1000;
    private final MyVectorStore vectorStore;
    private final ContextRepository contextRepository;
    private final ContextReranker contextReranker;

    public record RetrievalConfiguration(int topK, String collectionName, boolean useReranker,
            SelectStrategy selectStrategy) {
    }

    public record ChunkScorePair(Long chunkId, float score) {
    }

    public record ContextScorePair(Context context, float score) {
    }

    public ContextRetriever(MyVectorStore vectorStore, ContextRepository contextRepository,
            ContextReranker contextReranker) {
        this.vectorStore = vectorStore;
        this.contextRepository = contextRepository;
        this.contextReranker = contextReranker;
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
            List<Context> contexts = contextRepository.findAllByChunkIds(topNChunks.stream()
                    .map(ChunkScorePair::chunkId)
                    .toList());
            rankedContexts = contextReranker.rerank(contexts, query);
        } else {
            // If not using reranker, merge chunks into contexts and take the max chunk
            // score as the context score
            Map<Context, Float> contextScoreMap = new HashMap<>();
            topNChunks.stream().forEach(c -> {
                Context context = contextRepository.findByChunkId(c.chunkId());

                Float existingScore = contextScoreMap.getOrDefault(context, 0.0f);
                contextScoreMap.put(context, Math.max(existingScore, c.score()));
            });

            rankedContexts = contextScoreMap.entrySet().stream()
                    .map(e -> new ContextScorePair(e.getKey(), e.getValue()))
                    .sorted((a, b) -> Float.compare(b.score(), a.score()))
                    .toList();
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

}
