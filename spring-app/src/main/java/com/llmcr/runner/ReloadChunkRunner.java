package com.llmcr.runner;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.vectorstore.MyVectorStore;

/**
 * Reload all chunks from the database into the vector store. This is used to
 * align vector store with the database after a restart, and also to verify that
 * all chunks with embeddings
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "reload")
public class ReloadChunkRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReloadChunkRunner.class);

    private final ChunkCollectionRepository chunkCollectionRepository;
    private final MyVectorStore vectorStore;

    public ReloadChunkRunner(ChunkCollectionRepository chunkCollectionRepository, MyVectorStore vectorStore) {
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.vectorStore = vectorStore;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting vector store reload test runner");

        List<ChunkCollection> chunkCollections = chunkCollectionRepository.findAll();
        if (chunkCollections.isEmpty()) {
            log.info("No chunk collections found, skipping reload");
            return;
        }

        int totalAdded = 0;
        int totalSkippedMissingEmbedding = 0;

        for (ChunkCollection chunkCollection : chunkCollections) {
            String collectionName = chunkCollection.getName();

            List<Chunk> chunksToLoad = chunkCollection.getChunks().stream()
                    .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
                    .toList();

            int skippedMissingEmbedding = chunkCollection.getChunks().size() - chunksToLoad.size();
            if (!chunksToLoad.isEmpty()) {
                vectorStore.addChunks(chunksToLoad, collectionName);
            }

            totalAdded += chunksToLoad.size();
            totalSkippedMissingEmbedding += skippedMissingEmbedding;

            log.info("Loaded {} chunks into collection '{}', skipped {} chunks without embedding",
                    chunksToLoad.size(), collectionName, skippedMissingEmbedding);
        }

        log.info("Vector store load complete. Added {} chunks, skipped {} chunks without embedding",
                totalAdded, totalSkippedMissingEmbedding);
    }
}
