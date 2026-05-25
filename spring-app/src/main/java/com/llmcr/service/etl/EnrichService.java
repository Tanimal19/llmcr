package com.llmcr.service.etl;

import com.llmcr.entity.Context;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.etl.transformer.ContextEnricher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EnrichService {

    private static final Logger logger = LoggerFactory.getLogger(EnrichService.class);

    private final ContextRepository contextRepository;
    private final List<ContextEnricher> enrichers;

    public EnrichService(ContextRepository contextRepository, List<ContextEnricher> enrichers) {
        this.contextRepository = contextRepository;
        this.enrichers = enrichers;
    }

    @Transactional
    public void enrich(Long contextId) {
        Context context = contextRepository
            .findById(contextId)
            .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));
        if (context.isEnriched()) {
            logger.info("Context '{}' already context, skipping enriching", context.getName());
            return;
        }

        Integer originalChunkCount = context.getChunkCount();

        for (ContextEnricher enricher : enrichers) {
            if (!enricher.supports(context)) {
                continue;
            }
            try {
                context = enricher.apply(context);
            } catch (Exception e) {
                throw new RuntimeException("Error enriching context " + context.getName(), e);
            }
        }

        context.setEnriched(true);
        if (context.getChunkCount() != originalChunkCount) {
            context.setChunkLoaded(false);
        }
        contextRepository.save(context);
        logger.info("Context '{}' enriched", context.getName());
    }
}
