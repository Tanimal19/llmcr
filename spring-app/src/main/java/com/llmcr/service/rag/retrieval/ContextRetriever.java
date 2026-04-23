package com.llmcr.service.rag.retrieval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.repository.ChunkRepository;
import com.llmcr.service.rag.retrieval.rerank.RerankStrategy;
import com.llmcr.service.vectorstore.MyVectorStore;

public class ContextRetriever {

    private static final int TOP_N = 1000;
    private final MyVectorStore vectorStore;
    private final ChunkRepository chunkRepository;

    public record RetrievalConfiguration(int topK, String collectionName, RerankStrategy rerankStrategy) {
    }

    public record ChunkScorePair(Long chunkId, float score) {
    }

    public record ContextScorePair(Context context, float score) {
    }

    public ContextRetriever(MyVectorStore vectorStore, ChunkRepository chunkRepository) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
    }

    /**
     * Retrieve relevant contexts for the query. The retrieval process includes
     * these steps:
     * 1) retrieve topN relevant chunks from the vector store, and
     * 2) rerank the topN chunks and select topK chunks based on the specified
     * reranking strategy
     * 3) merge the topK chunks into contexts
     */
    public List<Context> retrieve(String query, RetrievalConfiguration config) {
        List<ChunkScorePair> topNChunks = vectorStore.similaritySearch(query, TOP_N, config.collectionName());
        List<ChunkScorePair> topKChunks = config.rerankStrategy.rerank(topNChunks, config.topK());

        // merge chunks into contexts
        Map<Context, Float> contextScoreMap = new HashMap<>();
        topKChunks.stream().forEach(c -> {
            Chunk chunk = chunkRepository.findById(c.chunkId()).orElse(null);
            Context context = chunk.getContext();

            Float existingScore = contextScoreMap.getOrDefault(context, 0.0f);
            // if a context has multiple chunks in the search results, take the max score as
            // the context score
            contextScoreMap.put(context, Math.max(existingScore, c.score()));
        });

        // sort contexts by score in descending order and return topK contexts
        return contextScoreMap.entrySet().stream()
                .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

}
