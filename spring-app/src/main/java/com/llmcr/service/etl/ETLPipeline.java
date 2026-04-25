package com.llmcr.service.etl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ETLPipeline.class);

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

        log.info("ETL pipeline started. Extractors: {}, Splitters: {}, Enrichers: {}",
                extractors.size(), splitters.size(), enrichers.size());

        // init chunk collections
        Set<String> allCollectionNames = contextTypeToCollectionNames.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());
        allCollectionNames.forEach(name -> {
            if (chunkCollectionRepository.findByName(name).isEmpty()) {
                chunkCollectionRepository.save(new ChunkCollection(name));
                log.debug("Created chunk collection: {}", name);
            }
        });
        log.info("[Extract] Starting extraction from {} source(s)", sourceRepository.count());

        // extract contexts from sources
        List<Context> extractedContexts = new ArrayList<>();
        sourceRepository.findAll().forEach(source -> {
            List<Context> contexts = new ArrayList<>();
            for (SourceExtractor extractor : extractors) {
                if (extractor.supports(source)) {
                    try {
                        List<Context> extracted = extractor.apply(source);
                        contexts.addAll(extracted);
                        log.debug("[Extract] {} extracted {} context(s) from source '{}'",
                                extractor.getClass().getSimpleName(), extracted.size(), source.getSourceName());
                    } catch (Exception e) {
                        throw new RuntimeException("Error extracting context from source " + source.getSourceName(), e);
                    }
                }
            }
            contextRepository.saveAll(contexts);
            extractedContexts.addAll(contexts);
        });
        log.info("[Extract] Done. Total contexts extracted: {}", extractedContexts.size());

        // split & load contexts
        log.info("[Split] Starting split phase on {} context(s)", extractedContexts.size());
        List<Context> contextsToSplit = contextRepository.findAll();
        contextsToSplit.forEach(context -> {
            Context splitted = context;
            for (ContextTransformer splitter : splitters) {
                try {
                    splitted = splitter.apply(splitted);
                } catch (Exception e) {
                    throw new RuntimeException("Error splitting context " + splitted.getName(), e);
                }
            }
            contextRepository.save(splitted);
            loadContext(splitted);
            log.debug("[Split] Context '{}' -> {} chunk(s)", splitted.getName(), splitted.getChunks().size());
        });
        log.info("[Split] Done. Loading split contexts into vector store");
        log.info("[Split] Vector store load complete");

        // enrich & load CLASSNODE contexts
        List<Context> contextsToEnrich = contextRepository.findByType(ContextType.CLASSNODE);
        log.info("[Enrich] Starting enrich phase on {} context(s)", contextsToEnrich.size());
        contextsToEnrich.forEach(context -> {
            Context enriched = context;
            for (ContextTransformer enricher : enrichers) {
                try {
                    enriched = enricher.apply(enriched);
                } catch (Exception e) {
                    throw new RuntimeException("Error enriching context " + enriched.getName(), e);
                }
            }
            contextRepository.save(enriched);
            loadContext(enriched);
            log.debug("[Enrich] Context '{}' enriched", enriched.getName());
        });
        log.info("[Enrich] Done. Loading enriched contexts into vector store");
        log.info("[Enrich] Vector store load complete");

        log.info("ETL pipeline finished successfully");
    }

    private void loadContext(Context context) {
        List<String> collectionNames = contextTypeToCollectionNames.getOrDefault(context.getType(), Set.of())
                .stream().toList();

        if (collectionNames.isEmpty()) {
            log.warn("[Load] Context '{}' (type={}) has no mapped collection, skipping",
                    context.getName(), context.getType());
            return;
        }

        for (String collectionName : collectionNames) {
            List<Chunk> chunks = context.getChunks();
            vectorStore.add(chunks, collectionName);
            ChunkCollection chunkCollection = chunkCollectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Chunk collection not found: " + collectionName));
            chunks.forEach(chunk -> chunk.addChunkCollection(chunkCollection));
            log.debug("[Load] Context '{}' -> {} chunk(s) added to collection '{}'",
                    context.getName(), chunks.size(), collectionName);
            contextRepository.save(context);
        }
    }
}
