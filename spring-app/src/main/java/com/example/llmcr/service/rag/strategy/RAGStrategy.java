package com.example.llmcr.service.rag.strategy;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

public interface RAGStrategy {

    public void setVectorStore(VectorStore vectorStore);

    public List<Document> retrieveRelevantChunks(String query, int topK);

}
