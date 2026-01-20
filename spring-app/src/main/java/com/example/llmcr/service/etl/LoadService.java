package com.example.llmcr.service.etl;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.llmcr.entity.Chunk;
import com.example.llmcr.entity.Chunk.ChunkType;
import com.example.llmcr.repository.DataStore;

public class LoadService {
    private final DataStore dataStore;
    private final VectorStore vectorStore;

    private static final Logger logger = Logger.getLogger(LoadService.class.getName());

    public LoadService(DataStore dataStore, VectorStore vectorStore) {
        this.dataStore = dataStore;
        this.vectorStore = vectorStore;
    }

    public void load(Set<ChunkType> loadedChunkTypes) {
        long startTime = System.currentTimeMillis();
        logger.info("Start data loading");

        for (ChunkType type : loadedChunkTypes) {
            List<Document> documents = dataStore.findChunksByType(type).stream()
                    .map(Chunk::toDocument)
                    .toList();

            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                logger.fine("+ Loaded " + documents.size() + " documents for type: " + type);
            }
        }

        long endTime = System.currentTimeMillis();
        logger.info("Data loading completed in " + (endTime - startTime) + "ms");
    }
}
