package com.example.llmcr.service.rag.retrieval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class AdaptiveKStrategy implements RetrievalStrategy {

    private final int topN = 1000;
    private final float garanteedScore = 0.55f; // documents with high scores will always be included
    private final float minScoreThreshold = 0.3f; // minimum score to consider

    public List<Document> retrieve(String query, int TopK, VectorStore vectorStore) {
        System.out.println("AdaptiveKStrategy: Retrieving documents for query: " + query);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topN)
                .build();

        List<Document> relevantDocuments = vectorStore.similaritySearch(request);
        relevantDocuments.sort((d1, d2) -> Float.compare((Float) d2.getMetadata().get("similarity_score"),
                (Float) d1.getMetadata().get("similarity_score")));

        // find largest gap
        int optimalK = findLargestGapIndex(relevantDocuments);
        // int selectedK = Math.min(optimalK, TopK);
        int selectedK = optimalK;

        System.out.println("AdaptiveKStrategy: Selected top K = " + selectedK);
        System.out.println("Top documents:\n" + relevantDocuments.stream().limit(selectedK + 5)
                .map(d -> d.getMetadata().get("chunk_id").toString() + "::"
                        + d.getMetadata().get("similarity_score").toString())
                .reduce((s1, s2) -> s1 + "\n" + s2).orElse(""));

        return new ArrayList<>(
                relevantDocuments.subList(0, Math.min(selectedK, relevantDocuments.size())));
    }

    private int findLargestGapIndex(List<Document> sortedDocuments) {
        float maxGap = 0;
        int gapIndex = 0;

        for (int i = 0; i < sortedDocuments.size() - 1; i++) {
            float score1 = (Float) sortedDocuments.get(i).getMetadata().get("similarity_score");
            if (score1 >= garanteedScore) {
                continue; // skip high-confidence documents
            } else if (score1 < minScoreThreshold) {
                break; // drop low-confidence documents
            }

            float score2 = (Float) sortedDocuments.get(i + 1).getMetadata().get("similarity_score");
            float gap = score1 - score2;
            if (gap > maxGap) {
                maxGap = gap;
                gapIndex = i;
            }
        }

        System.out.println("Max score gap (" + maxGap + ") at index = " + gapIndex);
        return gapIndex;
    }
}
