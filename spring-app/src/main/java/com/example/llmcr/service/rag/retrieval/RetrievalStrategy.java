package com.example.llmcr.service.rag.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

public interface RetrievalStrategy {
    public List<Document> retrieve(String query, int topK, VectorStore vectorStore);
}
