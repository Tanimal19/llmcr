package com.llmcr.service.etl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.model.EmbeddingClient;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.ContextRepository;
import com.llmcr.vectorstore.MyVectorStore;

@Service
public class LoadService {

    private static final Logger log = LoggerFactory.getLogger(LoadService.class);

    private final ChunkCollectionRepository chunkCollectionRepository;
    private final ContextRepository contextRepository;
    private final MyVectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    private final Map<ContextType, Set<String>> contextTypeToCollectionNames = Map.of(
            ContextType.CLASSNODE, Set.of("PROJECT_CONTEXT", "CLASSNODE"),
            ContextType.DOCUMENT, Set.of("PROJECT_CONTEXT", "DOCUMENT"),
            ContextType.USECASE, Set.of("USECASE"),
            ContextType.GUIDELINE, Set.of("GUIDELINE"));

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
    public void initCollections() {
        contextTypeToCollectionNames.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet())
                .forEach(name -> {
                    if (chunkCollectionRepository.findByName(name).isEmpty()) {
                        chunkCollectionRepository.save(new ChunkCollection(name));
                        log.info("Created chunk collection: {}", name);
                    }
                });
        chunkCollectionRepository.flush();
    }

    @Transactional
    public void loadContext(Context context) {
        List<String> collectionNames = contextTypeToCollectionNames
                .getOrDefault(context.getType(), Set.of())
                .stream()
                .toList();

        if (collectionNames.isEmpty()) {
            log.warn("Context '{}' (type={}) has no mapped collection, skipping",
                    context.getName(), context.getType());
            return;
        }

        List<Chunk> chunks = context.getChunks();

        for (Chunk chunk : chunks) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
                chunk.setEmbedding(embeddingClient.embed(chunk.getContent()));
            }
        }

        for (String collectionName : collectionNames) {
            ChunkCollection chunkCollection = chunkCollectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Chunk collection not found: " + collectionName));

            // Only add chunks that are not already in the collection to avoid duplicates in
            // the vector store.
            List<Chunk> chunksToAdd = chunks.stream()
                    .filter(chunk -> chunk.getChunkCollections().stream()
                            .map(ChunkCollection::getName)
                            .noneMatch(collectionName::equals))
                    .toList();

            vectorStore.addChunks(chunksToAdd, collectionName);
            chunksToAdd.forEach(chunk -> chunkCollection.addChunk(chunk));
            chunkCollectionRepository.save(chunkCollection);
        }

        log.info("Loaded context '{}' (id={}) with {} chunks into collections: {}",
                context.getName(), context.getId(), chunks.size(), collectionNames);

        contextRepository.save(context);
        contextRepository.flush();
    }
}
