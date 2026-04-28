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
public class EnrichService {

    private static final Logger log = LoggerFactory.getLogger(EnrichService.class);

    private final ContextRepository contextRepository;
    private final List<ContextTransformer> enrichers;

    public EnrichService(
            ContextRepository contextRepository,
            @Qualifier("enricherTransformer") List<ContextTransformer> enrichers) {
        this.contextRepository = contextRepository;
        this.enrichers = enrichers;
    }

    @Transactional
    public void enrich(Long contextId) {
        Context context = contextRepository.findById(contextId)
                .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));
        if (context.isEnriched()) {
            log.info("Context '{}' already context, skipping enriching", context.getName());
            return;
        }

        for (ContextTransformer enricher : enrichers) {
            try {
                context = enricher.apply(context);
            } catch (Exception e) {
                throw new RuntimeException("Error enriching context " + context.getName(), e);
            }
        }

        contextRepository.save(context);
        context.setEnriched(true);
        context.setChunkLoaded(false);
        log.info("Context '{}' enriched", context.getName());
    }
}
