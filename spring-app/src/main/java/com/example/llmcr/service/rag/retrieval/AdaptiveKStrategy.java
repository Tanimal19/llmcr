package com.example.llmcr.service.rag.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class AdaptiveKStrategy implements RetrievalStrategy {

    private final int topN = 1000;
    private final int maxTopK = 20;
    private final float garanteedScore = 0.55f; // documents with high scores will always be included

    public List<Document> retrieve(String query, int minTopK, VectorStore vectorStore) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topN)
                .build();

        List<Document> relevantDocuments = vectorStore.similaritySearch(request);

        // sort by similarity score descending
        relevantDocuments.sort((d1, d2) -> Float.compare((Float) d2.getMetadata().get("similarity_score"),
                (Float) d1.getMetadata().get("similarity_score")));

        // find largest gap
        float maxGap = 0;
        int optimalK = 0;
        for (int i = 0; i < relevantDocuments.size() - 1; i++) {
            float score1 = (Float) relevantDocuments.get(i).getMetadata().get("similarity_score");
            if (score1 >= garanteedScore) {
                optimalK = i + 1;
                continue;
            }

            float score2 = (Float) relevantDocuments.get(i + 1).getMetadata().get("similarity_score");
            float gap = score1 - score2;
            if (gap > maxGap) {
                maxGap = gap;
                optimalK = i + 1;
            }
        }
        int selectedK = Math.min(Math.max(optimalK, minTopK), maxTopK);

        System.out.println("Max score gap (" + maxGap + ") at K = " + optimalK + " , selected top K = " + selectedK);
        System.out.println("Top documents:\n" + relevantDocuments.stream().limit(selectedK + 5)
                .map(d -> d.getMetadata().get("chunk_id").toString() + "::"
                        + d.getMetadata().get("similarity_score").toString())
                .reduce((s1, s2) -> s1 + "\n" + s2).orElse(""));

        return relevantDocuments.subList(0, Math.min(selectedK, relevantDocuments.size()));
    }

}
