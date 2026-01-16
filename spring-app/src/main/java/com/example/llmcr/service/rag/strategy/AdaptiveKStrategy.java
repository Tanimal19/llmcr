package com.example.llmcr.service.rag.strategy;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class AdaptiveKStrategy implements RAGStrategy {

    private VectorStore vectorStore;
    private final int topN = 100;

    public AdaptiveKStrategy() {
    }

    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> retrieveRelevantChunks(String query, int topK) {
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
        int optimalK = topK;
        for (int i = 0; i < relevantDocuments.size() - 1; i++) {
            float score1 = (Float) relevantDocuments.get(i).getMetadata().get("similarity_score");
            float score2 = (Float) relevantDocuments.get(i + 1).getMetadata().get("similarity_score");
            float gap = score1 - score2;
            if (gap > maxGap) {
                maxGap = gap;
                optimalK = i + 1;
            }
        }

        System.out.println("AdaptiveKStrategy selected k = " + optimalK);
        System.out.println("Scores:" + relevantDocuments.stream()
                .map(d -> d.getMetadata().get("similarity_score").toString())
                .reduce((s1, s2) -> s1 + ", " + s2).orElse(""));

        return relevantDocuments.subList(0, Math.min(optimalK, relevantDocuments.size()));
    }

}
