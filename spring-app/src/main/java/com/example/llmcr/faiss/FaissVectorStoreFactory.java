package com.example.llmcr.faiss;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.llmcr.repository.DataStore;

@Component
public class FaissVectorStoreFactory {

    private final DataStore dataStore;
    private final FaissService faissService;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public FaissVectorStoreFactory(
            DataStore dataStore,
            FaissService faissService,
            EmbeddingModel embeddingModel) {
        this.dataStore = dataStore;
        this.faissService = faissService;
        this.embeddingModel = embeddingModel;
    }

    public FaissVectorStore create(String indexName) {
        return new FaissVectorStore(
                dataStore,
                faissService,
                embeddingModel,
                indexName);
    }
}