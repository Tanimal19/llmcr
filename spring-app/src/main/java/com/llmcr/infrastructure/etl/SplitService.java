package com.llmcr.infrastructure.etl;

import com.llmcr.domain.entity.Context;
import com.llmcr.domain.repository.ContextRepository;
import com.llmcr.infrastructure.etl.transformer.ContextSplitter;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SplitService {

    private static final Logger logger = LoggerFactory.getLogger(SplitService.class);

    private final ContextRepository contextRepository;
    private final List<ContextSplitter> splitters;

    public SplitService(ContextRepository contextRepository, List<ContextSplitter> splitters) {
        this.contextRepository = contextRepository;
        this.splitters = splitters;
    }

    @Transactional
    public void split(Long contextId) {
        Context context = contextRepository
                .findById(contextId)
                .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));
        if (context.isSplitted()) {
            logger.info("Context '{}' already loaded, skipping splitting", context.getName());
            return;
        }

        for (ContextSplitter splitter : splitters) {
            if (!splitter.supports(context)) {
                continue;
            }
            try {
                context = splitter.apply(context);
            } catch (Exception e) {
                throw new RuntimeException("Error splitting context " + context.getName(), e);
            }
        }
        context.setSplitted(true);
        context.setChunkLoaded(false);
        contextRepository.save(context);
        logger.info("Split context '{}' -> {} chunk(s)", context.getName(), context.getChunks().size());
    }
}
