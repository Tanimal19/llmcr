package com.example.llmcr.service.rag.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class SimpleRAGStrategy implements RetrievalStrategy {
    public List<Document> retrieve(String query, int topK, VectorStore vectorStore) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        return vectorStore.similaritySearch(request);
    }
}
