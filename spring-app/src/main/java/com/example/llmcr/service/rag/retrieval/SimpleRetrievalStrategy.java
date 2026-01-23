package com.example.llmcr.service.rag.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class SimpleRetrievalStrategy implements RetrievalStrategy {
    public List<Document> retrieve(String query, int topK, VectorStore vectorStore) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK + 20) // Retrieve more to allow for later re-ranking
                .build();

        List<Document> relevantDocuments = vectorStore.similaritySearch(request);
        relevantDocuments.sort((d1, d2) -> Float.compare((Float) d2.getMetadata().get("similarity_score"),
                (Float) d1.getMetadata().get("similarity_score")));

        return relevantDocuments.subList(0, Math.min(topK, relevantDocuments.size()));
    }
}
