package com.example.llmcr.service.rag.strategy;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class BaseRAGStrategy implements RAGStrategy {

    private VectorStore vectorStore;

    public BaseRAGStrategy() {
    }

    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> retrieveRelevantChunks(String query, int topK) {
        // Default implementation (can be overridden by subclasses)
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        return vectorStore.similaritySearch(request);
    }
}
