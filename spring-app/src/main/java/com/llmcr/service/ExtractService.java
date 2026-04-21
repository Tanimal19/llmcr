package com.llmcr.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.llmcr.entity.Context;
import com.llmcr.repository.*;
import com.llmcr.extractor.*;

/**
 * Service responsible for extracting context from sources.
 */
@Service
public class ExtractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractService.class);

    private final SourceRepository sourceRepository;
    private final ContextRepository contextRepository;

    private List<ContextExtractor<? extends Context>> extractors;

    @Autowired
    public ExtractService(SourceRepository sourceRepository,
            ContextRepository contextRepository) {
        this.sourceRepository = sourceRepository;
        this.contextRepository = contextRepository;
        this.extractors = List.of(
                new ClassNodeExtractor(),
                new DocumentExtractor(2000),
                new UsecaseExtractor(),
                new GuidelineExtractor());
    }

    public void extract() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Start data extraction");

        sourceRepository.findAll().forEach(source -> {
            for (ContextExtractor<? extends Context> extractor : extractors) {
                if (extractor.supports(source)) {
                    try {
                        List<? extends Context> contexts = extractor.apply(source);
                        contextRepository.saveAll(contexts);
                        LOGGER.info("{} extracted {} context from source {}",
                                extractor.getClass().getSimpleName(), contexts.size(), source.getSourceName());
                    } catch (Exception e) {
                        LOGGER.error("Error extracting context from source {}: {}",
                                source.getSourceName(), e.getMessage());
                    }
                }
            }
        });

        long endTime = System.currentTimeMillis();
        LOGGER.info("Data extraction completed in " + (endTime - startTime) + "ms");
    }

}
