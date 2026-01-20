package com.example.llmcr.service.rag.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class AdaptiveKStrategy implements RetrievalStrategy {

    private final int topN = 1000;
    private final float highConfidenceScore = 0.6f;
    private final float lowConfidenceScore = 0.3f;

    private static final Logger logger = Logger.getLogger(AdaptiveKStrategy.class.getName());

    public List<Document> retrieve(String query, int topK, VectorStore vectorStore) {
        logger.info("Query: " + query);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topN)
                .build();

        List<Document> relevantDocuments = vectorStore.similaritySearch(request);
        relevantDocuments.sort((d1, d2) -> Float.compare(
                (Float) d2.getMetadata().get("similarity_score"),
                (Float) d1.getMetadata().get("similarity_score")));

        // find largest gap
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

        logger.info("Find optimalK = " + optimalK + " with max gap = " + maxGap + ")");
        return new ArrayList<>(
                relevantDocuments.subList(0, Math.min(optimalK, relevantDocuments.size())));
    }
}
