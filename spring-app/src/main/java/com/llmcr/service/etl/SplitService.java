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

    public SplitService(
            ContextRepository contextRepository,
            @Qualifier("splitterTransformer") List<ContextTransformer> splitters) {
        this.contextRepository = contextRepository;
        this.splitters = splitters;
    }

    @Transactional
    public void split(Long contextId) {
        Context context = contextRepository.findById(contextId)
                .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));
        if (context.isSplitted()) {
            log.info("Context '{}' already loaded, skipping splitting", context.getName());
            return;
        }

        for (ContextTransformer splitter : splitters) {
            try {
                context = splitter.apply(context);
            } catch (Exception e) {
                throw new RuntimeException("Error splitting context " + context.getName(), e);
            }
        }
        contextRepository.save(context);
        context.setSplitted(true);
        context.setChunkLoaded(false);
        contextRepository.flush();
        log.info("Context '{}' -> {} chunk(s)", context.getName(), context.getChunks().size());
    }
}