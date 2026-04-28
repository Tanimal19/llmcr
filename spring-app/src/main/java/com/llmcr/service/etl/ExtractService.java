package com.llmcr.service.etl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Context;
import com.llmcr.entity.Source;
import com.llmcr.repository.ContextRepository;
import com.llmcr.repository.SourceRepository;
import com.llmcr.service.etl.extractor.SourceExtractor;

@Service
public class ExtractService {

    private static final Logger log = LoggerFactory.getLogger(ExtractService.class);

    private final SourceRepository sourceRepository;
    private final ContextRepository contextRepository;
    private final List<SourceExtractor> extractors;

    public ExtractService(
            SourceRepository sourceRepository,
            ContextRepository contextRepository,
            List<SourceExtractor> extractors) {
        this.sourceRepository = sourceRepository;
        this.contextRepository = contextRepository;
        this.extractors = extractors;

        log.info("ExtractService initialized with {} extractors: {}",
                extractors.size(),
                extractors.stream()
                        .map(e -> e.getClass().getSimpleName())
                        .toList());
    }

    @Transactional
    public void extract(Long sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new RuntimeException("Source not found: " + sourceId));

        if (source.isExtracted()) {
            log.info("Source '{}' already extracted, skipping", source.getSourceName());
            return;
        }

        log.info("Start extracting context from source '{}'", source.getSourceName());
        List<Context> contexts = new ArrayList<>();
        for (SourceExtractor extractor : extractors) {
            if (extractor.supports(source)) {
                try {
                    List<Context> extracted = extractor.apply(source);
                    contexts.addAll(extracted);
                    log.info("{} extracted {} context(s) from source '{}'",
                            extractor.getClass().getSimpleName(), extracted.size(), source.getSourceName());
                } catch (Exception e) {
                    throw new RuntimeException("Error extracting context from source " + source.getSourceName(), e);
                }
            }
        }
        if (!contexts.isEmpty()) {
            contextRepository.saveAll(contexts);
        }
        source.setExtracted(true);
        sourceRepository.save(source);
    }
}