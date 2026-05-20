package com.llmcr.service.etl;

import java.util.HashSet;
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

    @Transactional
    public void load(Long contextId) {
        Context context = contextRepository.findById(contextId)
                .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));
        if (context.isChunkLoaded()) {
            logger.info("Context '{}' is already loaded, skipping", context.getName(), context.getId());
            return;
        }

        Set<ChunkCollection> inCollections = context.getSource().getTrackRoot().getInCollections();
        if (inCollections.isEmpty()) {
            inCollections = new HashSet<>(chunkCollectionRepository.findAll());
        }

        // generate embeddings for chunks if not exist
        List<Chunk> chunks = context.getChunks();
        for (Chunk chunk : chunks) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
                chunk.setEmbedding(embeddingClient.embed(chunk.getContent()));
            }
            chunkRepository.save(chunk);
        }

        for (ChunkCollection chunkCollection : inCollections) {
            // Only add chunks that are not already in the collection to avoid duplicates in
            // the vector store.
            List<Chunk> chunksToAdd = chunks.stream()
                    .filter(chunk -> chunk.getChunkCollections().stream()
                            .noneMatch(c -> c.getId().equals(chunkCollection.getId())))
                    .toList();

            vectorStore.addChunks(chunksToAdd, chunkCollection.getName());
            chunksToAdd.forEach(chunk -> chunkCollection.addChunk(chunk));
            chunkCollectionRepository.save(chunkCollection);
            logger.info("Loaded '{}' new chunks of '{}' into collection '{}'.", chunksToAdd.size(),
                    context.getName(), chunkCollection.getName());
        }

        context.setChunkLoaded(true);
        contextRepository.save(context);
    }

    /**
     * This method is used to reload all chunks into vector store. This is useful
     * when there are changes in chunkCollections.
     */
    @Transactional
    public void reloadAll() {
        logger.info("Start reloading chunks to vector store");

        List<ChunkCollection> chunkCollections = chunkCollectionRepository.findAll();
        if (chunkCollections.isEmpty()) {
            logger.info("No chunk collections found, skipping reload");
            return;
        }

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

        logger.info("Vector store reload complete. Added {} chunks, skipped {} chunks without embedding",
                totalAdded, totalSkippedMissingEmbedding);
    }
}
