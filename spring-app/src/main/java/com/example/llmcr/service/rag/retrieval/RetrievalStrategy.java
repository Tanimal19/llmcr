package com.example.llmcr.service.rag.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.llmcr.service.rag.retrieval.fusion.FusionStrategy;

public interface RetrievalStrategy {
    public List<Document> retrieve(String query, int TopK, VectorStore vectorStore);

    public default List<Document> retrieveAll(List<String> queries, int TopK, VectorStore vectorStore,
            FusionStrategy fusionStrategy) {
        List<List<Document>> allResults = queries.stream()
                .map(query -> retrieve(query, TopK, vectorStore))
                .toList();
        return fusionStrategy.fuse(allResults, TopK);
    }
}
