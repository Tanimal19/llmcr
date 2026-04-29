package com.llmcr.service.etl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.Context;
import com.llmcr.model.EmbeddingClient;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.ContextRepository;
import com.llmcr.vectorstore.MyVectorStore;

/**
 * Load chunks of a context into the vector store based on the TrackRoot's
 * in-collections.
 */
@Service
public class LoadService {

    private static final Logger log = LoggerFactory.getLogger(LoadService.class);

    private final ChunkCollectionRepository chunkCollectionRepository;
    private final ContextRepository contextRepository;
    private final MyVectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    public LoadService(
            ChunkCollectionRepository chunkCollectionRepository,
            ContextRepository contextRepository,
            MyVectorStore vectorStore,
            EmbeddingClient embeddingClient) {
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.contextRepository = contextRepository;
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
    }

    @Transactional
    public void load(Long contextId) {
        Context context = contextRepository.findById(contextId)
                .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));
        if (context.isChunkLoaded()) {
            log.info("Context '{}' is already loaded, skipping", context.getName(), context.getId());
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
            log.info("Loaded '{}' new chunks of '{}' into collection '{}'.", chunksToAdd.size(),
                    context.getName(), chunkCollection.getName());
        }

        context.setChunkLoaded(true);
        contextRepository.save(context);
    }
}
