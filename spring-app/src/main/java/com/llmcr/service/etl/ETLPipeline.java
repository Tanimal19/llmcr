package com.llmcr.service.etl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.llmcr.entity.*;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.repository.*;
import com.llmcr.service.etl.extractor.SourceExtractor;
import com.llmcr.service.etl.transformer.ContextTransformer;
import com.llmcr.vectorstore.MyVectorStore;

@Service
public class ETLPipeline {

    private final SourceRepository sourceRepository;
    private final ContextRepository contextRepository;
    private final ChunkCollectionRepository chunkCollectionRepository;
    private final MyVectorStore vectorStore;

    private final List<SourceExtractor> extractors;
    private final List<ContextTransformer> splitters;
    private final List<ContextTransformer> enrichers;

    private final Map<ContextType, Set<String>> contextTypeToCollectionNames = Map.of(
            ContextType.CLASSNODE, Set.of("PROJECT_CONTEXT", "CLASSNODE"),
            ContextType.DOCUMENT, Set.of("PROJECT_CONTEXT", "DOCUMENT"),
            ContextType.USECASE, Set.of("USECASE"),
            ContextType.GUIDELINE, Set.of("GUIDELINE"));

    public ETLPipeline(
            SourceRepository sourceRepository, ContextRepository contextRepository,
            ChunkCollectionRepository chunkCollectionRepository, MyVectorStore vectorStore,
            List<SourceExtractor> extractors,
            @Qualifier("splitterTransformer") List<ContextTransformer> splitters,
            @Qualifier("enricherTransformer") List<ContextTransformer> enrichers) {
        this.sourceRepository = sourceRepository;
        this.contextRepository = contextRepository;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.vectorStore = vectorStore;
        this.extractors = extractors;
        this.splitters = splitters;
        this.enrichers = enrichers;
    }

    public void run() {

        // init chunk collections
        contextTypeToCollectionNames.values().stream().flatMap(Set::stream).collect(Collectors.toSet())
                .forEach(name -> {
                    if (chunkCollectionRepository.findByName(name).isEmpty()) {
                        chunkCollectionRepository.save(new ChunkCollection(name));
                    }
                });

        // extract contexts from sources
        sourceRepository.findAll().stream().forEach(source -> {
            List<Context> contexts = new ArrayList<>();
            for (SourceExtractor extractor : extractors) {
                if (extractor.supports(source)) {
                    try {
                        contexts.addAll(extractor.apply(source));
                    } catch (Exception e) {
                        throw new RuntimeException("Error extracting context from source " + source.getSourceName(), e);
                    }
                }
            }
            contextRepository.saveAll(contexts);
        });

        // split & load contexts
        contextRepository.findAll().stream().forEach(context -> {
            Context splitted = context;
            for (ContextTransformer splitter : splitters) {
                try {
                    splitted = splitter.apply(splitted);
                    contextRepository.save(splitted);
                } catch (Exception e) {
                    throw new RuntimeException("Error splitting context " + splitted.getName(), e);
                }
            }
        });
        loadAllContexts();

        // enrich & load contexts
        contextRepository.findAll().stream().forEach(context -> {
            Context enriched = context;
            for (ContextTransformer enricher : enrichers) {
                try {
                    enriched = enricher.apply(enriched);
                    contextRepository.save(enriched);
                } catch (Exception e) {
                    throw new RuntimeException("Error enriching context " + enriched.getName(), e);
                }
            }
            contextRepository.save(enriched);
        });
        loadAllContexts();
    }

    private void loadAllContexts() {
        contextRepository.findAll().stream().forEach(context -> {

            List<String> collectionNames = contextTypeToCollectionNames.getOrDefault(context.getType(), Set.of())
                    .stream().toList();

            if (collectionNames.isEmpty()) {
                return;
            }

            for (String collectionName : collectionNames) {
                List<Chunk> chunks = context.getChunks();
                vectorStore.add(chunks, collectionName);
                ChunkCollection chunkCollection = chunkCollectionRepository.findByName(collectionName)
                        .orElseThrow(() -> new RuntimeException("Chunk collection not found: " + collectionName));
                chunks.forEach(chunk -> {
                    chunk.addChunkCollection(chunkCollection);
                });
                contextRepository.save(context);
            }
        });
    }
}
