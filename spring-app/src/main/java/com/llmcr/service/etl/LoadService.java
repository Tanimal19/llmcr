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
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.ContextRepository;
import com.llmcr.vectorstore.MyVectorStore;

@Service
public class LoadService {

    private static final Logger log = LoggerFactory.getLogger(LoadService.class);

    private final ChunkCollectionRepository chunkCollectionRepository;
    private final ContextRepository contextRepository;
    private final MyVectorStore vectorStore;

    private final Map<ContextType, Set<String>> contextTypeToCollectionNames = Map.of(
            ContextType.CLASSNODE, Set.of("PROJECT_CONTEXT", "CLASSNODE"),
            ContextType.DOCUMENT, Set.of("PROJECT_CONTEXT", "DOCUMENT"),
            ContextType.USECASE, Set.of("USECASE"),
            ContextType.GUIDELINE, Set.of("GUIDELINE"));

    public LoadService(
            ChunkCollectionRepository chunkCollectionRepository,
            ContextRepository contextRepository,
            MyVectorStore vectorStore) {
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.contextRepository = contextRepository;
        this.vectorStore = vectorStore;
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
        for (String collectionName : collectionNames) {
            vectorStore.add(chunks, collectionName);
            ChunkCollection chunkCollection = chunkCollectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Chunk collection not found: " + collectionName));
            chunks.forEach(chunk -> chunk.addChunkCollection(chunkCollection));
            log.info("Context '{}' -> {} chunk(s) added to collection '{}'",
                    context.getName(), chunks.size(), collectionName);
        }

        contextRepository.save(context);
    }
}
