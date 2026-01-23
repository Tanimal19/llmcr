package com.example.llmcr.service.etl;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.llmcr.entity.Embedding;
import com.example.llmcr.entity.Embedding.EmbeddingContentType;
import com.example.llmcr.repository.DataStore;

public class LoadService {
    private final DataStore dataStore;
    private final VectorStore vectorStore;

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadService.class);

    public LoadService(DataStore dataStore, VectorStore vectorStore) {
        this.dataStore = dataStore;
        this.vectorStore = vectorStore;
    }

    public void load(Set<EmbeddingContentType> loadedEmbeddingTypes) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Start data loading");

        for (EmbeddingContentType type : loadedEmbeddingTypes) {
            List<Document> documents = dataStore.findAllEmbeddingsByContentType(type).stream()
                    .map(Embedding::toDocument)
                    .toList();

            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                LOGGER.info("+ Loaded " + documents.size() + " documents for type: " + type);
            }
        }

        long endTime = System.currentTimeMillis();
        LOGGER.info("Data loading completed in " + (endTime - startTime) + "ms");
    }
}
