package com.llmcr.service.rag.retrieval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.SearchRequest;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.service.vectorstore.MyVectorStore;
import com.llmcr.service.vectorstore.MyVectorStore.ContextHolder;

public class FixedKStrategy implements RetrievalStrategy {
    public List<Context> retrieve(String query, int topK, MyVectorStore vectorStore) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK + 20) // Retrieve more to allow for later re-ranking
                .build();

        List<Long> chunkIds = res.ids();
        List<Float> chunkScores = res.scores();
        Map<Long, Float> idToScore = new HashMap<>();
        for (int i = 0; i < chunkIds.size(); i++) {
            idToScore.put(chunkIds.get(i), chunkScores.get(i));
        }

        class ContextHolder {
            List<Long> chunkIds = new ArrayList<>();
            float score = 0.0f;
        }
        Map<Context, ContextHolder> contextMap = new HashMap<>();

        // retrieve context using chunks
        chunkIds.stream().forEach(id -> {
            Chunk chunk = chunkRepository.findById(id).orElse(null);
            Context context = chunk.getContext();

            ContextHolder holder = contextMap.computeIfAbsent(context, k -> new ContextHolder());
            holder.chunkIds.add(chunk.getId());
            // if a context has multiple chunks in the search results, take the max score as
            // the context score
            holder.score = Math.max(holder.score, idToScore.get(chunk.getId()));
        });

        Map<Context, ContextHolder> contextHolderMap = vectorStore.similaritySearch(request);
        List<Document> relevantDocuments = vectorStore.similaritySearch(request);
        relevantDocuments.sort((d1, d2) -> Float.compare((Float) d2.getMetadata().get("similarity_score"),
                (Float) d1.getMetadata().get("similarity_score")));

        return relevantDocuments.subList(0, Math.min(topK, relevantDocuments.size()));
    }
}
