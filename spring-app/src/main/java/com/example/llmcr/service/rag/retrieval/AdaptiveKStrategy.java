package com.example.llmcr.service.rag.retrieval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class AdaptiveKStrategy implements RetrievalStrategy {

    private final int topN = 500;
    private final int buffer = 5;
    private final float highConfidenceScore = 0.6f;
    private final float lowConfidenceScore = 0.3f;

    public List<Document> retrieve(String query, int topK, VectorStore vectorStore) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topN)
                .build();

        // find topN
        List<Document> relevantDocuments = vectorStore.similaritySearch(request);
        relevantDocuments.sort((d1, d2) -> Float.compare(
                (Float) d2.getMetadata().get("similarity_score"),
                (Float) d1.getMetadata().get("similarity_score")));

        // find optimalK
        // FUTURE WORK: currently we select optimalK based on the same similarity
        // scores. In future, we can use a more powerful method to determine similarity
        // between the query and topN documents.
        float maxGap = 0;
        int optimalK = 0;
        for (int i = 0; i < relevantDocuments.size() - 1; i++) {
            float score1 = (Float) relevantDocuments.get(i).getMetadata().get("similarity_score");
            if (score1 >= highConfidenceScore) {
                optimalK = i + 1;
                continue; // always include high-confidence documents
            } else if (score1 < lowConfidenceScore) {
                break; // drop all low-confidence documents
            }

            float score2 = (Float) relevantDocuments.get(i + 1).getMetadata().get("similarity_score");
            float gap = score1 - score2;
            if (gap > maxGap) {
                maxGap = gap;
                optimalK = i + 1;
            }
        }
        System.out.println("Adaptive K: Find optimalK = " + optimalK + " with max gap = " + maxGap + ")");

        return new ArrayList<>(
                relevantDocuments.subList(0, Math.min(Math.min(optimalK + buffer, topK), relevantDocuments.size())));
    }
}
