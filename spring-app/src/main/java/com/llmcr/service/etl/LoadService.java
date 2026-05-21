package com.llmcr.service.etl;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.config.ApplicationProperties;
import com.llmcr.entity.Chunk;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.Context;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.ChunkRepository;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.vectorstore.MyVectorStore;

@Component
public class LoadService {

    private static final Logger logger = LoggerFactory.getLogger(LoadService.class);

    private final ChunkCollectionRepository chunkCollectionRepository;
    private final ContextRepository contextRepository;
    private final ChunkRepository chunkRepository;
    private final MyVectorStore vectorStore;
    private final EmbeddingModel embeddingClient;

    public LoadService(
            ApplicationProperties applicationProperties,
            ChunkCollectionRepository chunkCollectionRepository,
            ContextRepository contextRepository,
            ChunkRepository chunkRepository,
            MyVectorStore vectorStore,
            ModelClientFactory modelClientFactory) {
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.contextRepository = contextRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStore = vectorStore;
        this.embeddingClient = modelClientFactory.createEmbeddingModel(
                applicationProperties.getEmbeddingModel().getProvider(),
                applicationProperties.getEmbeddingModel().getName());
    }

    /**
     * This method do the following:
     * 1. Generate embeddings for all chunks of the context if not exist
     * 2. Save them to the database
     * 3. Add the chunks to the vector store under the collections defined by the
     * track root of the context's source.
     */
    @Transactional
    public void loadContextChunks(Long contextId) {
        Context context = contextRepository.findById(contextId)
                .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));
        if (context.isChunkLoaded()) {
            logger.info("Context '{}' is already loaded, skipping", context.getName(), context.getId());
            return;
        }

        // generate embeddings for chunks if not exist
        Set<Chunk> chunks = context.getChunks();
        for (Chunk chunk : chunks) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
                chunk.setEmbedding(embeddingClient.embed(chunk.getContent()));
            }
            chunkRepository.save(chunk);
        }

        // load chunks into vector store
        Set<ChunkCollection> targetCollections = context.getSource().getTrackRoot().getInCollections();
        for (ChunkCollection chunkCollection : targetCollections) {
            // only load chunks that are not already in the collection to avoid duplication
            // in vector store
            Set<Chunk> collectionChunks = chunkCollection.getChunks();
            List<Chunk> chunksToAdd = chunks.stream()
                    .filter(chunk -> collectionChunks.contains(chunk))
                    .toList();

            if (!chunksToAdd.isEmpty()) {
                vectorStore.addChunks(chunksToAdd, chunkCollection.getName());
                logger.info("Loaded {} chunks of context '{}' to collection '{}'", chunksToAdd.size(),
                        context.getName(), chunkCollection.getName());
            }
        }

        context.setChunkLoaded(true);
        contextRepository.save(context);
    }

    /**
     * This method do the following:
     * 1. Recompute what chunks should be in the collection based on the track roots
     * in the collection and the contexts related to those track roots.
     * 2. Reload the chunks into the vector store by removing all existing chunks of
     * the collection from the vector store and adding the new chunks to the vector
     * store.
     */
    @Transactional
    public void reloadAllChunks() {
        logger.info("Start reloading chunks to vector store");

        List<ChunkCollection> chunkCollections = chunkCollectionRepository.findAll();
        if (chunkCollections.isEmpty()) {
            logger.info("No chunk collections found, skipping reload");
            return;
        }

        // reset collections in both database and vector store
        chunkCollections.forEach(collection -> {
            collection.clearChunks();
            vectorStore.removeCollection(collection.getName());
        });
        chunkCollectionRepository.saveAll(chunkCollections);

        // recompute chunks in collections based on track roots
        contextRepository.findAll().forEach(context -> {
            Set<ChunkCollection> inCollections = context.getSource().getTrackRoot().getInCollections();
            for (ChunkCollection chunkCollection : inCollections) {
                chunkCollection.addChunks(context.getChunks());
            }
            chunkCollectionRepository.saveAll(inCollections);
        });

        logger.info("Reloading chunks into vector store");
        int totalAdded = 0;
        int totalSkippedMissingEmbedding = 0;
        for (ChunkCollection chunkCollection : chunkCollections) {
            String collectionName = chunkCollection.getName();

            Set<Chunk> collectionChunks = chunkCollection.getChunks();
            List<Chunk> chunksToLoad = collectionChunks.stream()
                    .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
                    .toList();

            int skippedMissingEmbedding = collectionChunks.size() - chunksToLoad.size();
            if (!chunksToLoad.isEmpty()) {
                vectorStore.addChunks(chunksToLoad, collectionName);
            }

            totalAdded += chunksToLoad.size();
            totalSkippedMissingEmbedding += skippedMissingEmbedding;

            logger.info("Loaded {} chunks into collection '{}', skipped {} chunks without embedding",
                    chunksToLoad.size(), collectionName, skippedMissingEmbedding);
        }

        logger.info("Vector store reload complete. Added {} chunks, skipped {} chunks without embedding",
                totalAdded, totalSkippedMissingEmbedding);
    }
}
