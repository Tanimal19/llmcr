package com.llmcr.service.etl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Context;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.etl.transformer.ContextTransformer;

@Service
public class SplitService {

    private static final Logger log = LoggerFactory.getLogger(SplitService.class);

    private final ContextRepository contextRepository;
    private final List<ContextTransformer> splitters;
    private final LoadService loadService;

    public SplitService(
            ContextRepository contextRepository,
            @Qualifier("splitterTransformer") List<ContextTransformer> splitters,
            LoadService loadService) {
        this.contextRepository = contextRepository;
        this.splitters = splitters;
        this.loadService = loadService;
    }

    @Transactional
    public void splitAndLoad(Long contextId) {
        Context splitted = contextRepository.findById(contextId)
                .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));

        for (ContextTransformer splitter : splitters) {
            try {
                splitted = splitter.apply(splitted);
            } catch (Exception e) {
                throw new RuntimeException("Error splitting context " + splitted.getName(), e);
            }
        }

        contextRepository.save(splitted);
        loadService.loadContext(splitted);
        contextRepository.flush();

        log.info("Context '{}' -> {} chunk(s)", splitted.getName(), splitted.getChunks().size());
    }
}