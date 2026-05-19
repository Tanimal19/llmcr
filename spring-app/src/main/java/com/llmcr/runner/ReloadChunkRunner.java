package com.llmcr.runner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.ContextRepository;
import com.llmcr.vectorstore.MyVectorStore;

/**
 * Reload all chunks from the database into the vector store. This is used to
 * align vector store with the database after a restart, and also to verify that
 * all chunks with embeddings
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "reload")
public class ReloadChunkRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ReloadChunkRunner.class);

    private final ChunkCollectionRepository chunkCollectionRepository;
    private final ContextRepository contextRepository;
    private final MyVectorStore vectorStore;

    public ReloadChunkRunner(
            ChunkCollectionRepository chunkCollectionRepository,
            ContextRepository contextRepository,
            MyVectorStore vectorStore) {
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.contextRepository = contextRepository;
        this.vectorStore = vectorStore;
    }

    @Override
    @Transactional
    public void run(String... args) {
        logger.info("Starting vector store reload runner");

        logger.info("Recompute chunk in chunkCollection");
        contextRepository.findAll().forEach(context -> {
            Set<ChunkCollection> inCollections = context.getSource().getTrackRoot().getInCollections();
            if (inCollections.isEmpty()) {
                inCollections = new HashSet<>(chunkCollectionRepository.findAll());
            }

            for (ChunkCollection chunkCollection : inCollections) {
                List<Chunk> chunks = context.getChunks();
                List<Chunk> chunksToAdd = chunks.stream()
                        .filter(chunk -> chunk.getChunkCollections().stream()
                                .noneMatch(c -> c.getId().equals(chunkCollection.getId())))
                        .toList();
                chunksToAdd.forEach(chunk -> chunkCollection.addChunk(chunk));
            }

            contextRepository.save(context);
        });

        logger.info("Reloading chunks into vector store");
        List<ChunkCollection> chunkCollections = chunkCollectionRepository.findAll();
        if (chunkCollections.isEmpty()) {
            logger.info("No chunk collections found, skipping reload");
            return;
        }

        int totalAdded = 0;
        int totalSkippedMissingEmbedding = 0;

        for (ChunkCollection chunkCollection : chunkCollections) {
            String collectionName = chunkCollection.getName();

            vectorStore.removeCollection(collectionName);

            List<Chunk> chunksToLoad = chunkCollection.getChunks().stream()
                    .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
                    .toList();

            int skippedMissingEmbedding = chunkCollection.getChunks().size() - chunksToLoad.size();
            if (!chunksToLoad.isEmpty()) {
                vectorStore.addChunks(chunksToLoad, collectionName);
            }

            totalAdded += chunksToLoad.size();
            totalSkippedMissingEmbedding += skippedMissingEmbedding;

            logger.info("Loaded {} chunks into collection '{}', skipped {} chunks without embedding",
                    chunksToLoad.size(), collectionName, skippedMissingEmbedding);
        }

        logger.info("Vector store load complete. Added {} chunks, skipped {} chunks without embedding",
                totalAdded, totalSkippedMissingEmbedding);
    }
}
