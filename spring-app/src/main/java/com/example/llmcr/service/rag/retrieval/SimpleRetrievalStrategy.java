package com.example.llmcr.service.rag.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class SimpleRetrievalStrategy implements RetrievalStrategy {
    public List<Document> retrieve(String query, int topK, VectorStore vectorStore) {
        System.out.println("SimpleRAGStrategy: Retrieving documents for query: " + query);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        List<Document> relevantDocuments = vectorStore.similaritySearch(request);
        relevantDocuments.sort((d1, d2) -> Float.compare((Float) d2.getMetadata().get("similarity_score"),
                (Float) d1.getMetadata().get("similarity_score")));

        return relevantDocuments;
    }
}
