package com.llmcr.service.etl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.llmcr.repository.ContextRepository;

@Service
public class ETLPipeline {

    private static final Logger log = LoggerFactory.getLogger(ETLPipeline.class);

    private final ContextRepository contextRepository;
    private final ExtractService extractService;
    private final SplitService splitService;
    private final EnrichService enrichService;
    private final LoadService loadService;

    public ETLPipeline(
            ContextRepository contextRepository,
            ExtractService extractService,
            SplitService splitService,
            EnrichService enrichService,
            LoadService loadService) {
        this.contextRepository = contextRepository;
        this.extractService = extractService;
        this.splitService = splitService;
        this.enrichService = enrichService;
        this.loadService = loadService;
    }

    public void run(List<Long> sourceIds) {
        log.info("ETL pipeline started");

        loadService.initCollections();

        sourceIds.forEach(id -> extractService.extract(id));

        sourceIds.forEach(id -> contextRepository.findAllIdsBySourceId(id)
                .forEach(contextId -> splitService.splitAndLoad(contextId)));

        // enrich must be performed on all contexts after splitting, since the
        // enrichment may require the complete set of chunks in a context.
        contextRepository.findAllIds().forEach(id -> enrichService.enrichAndLoad(id));
    }
}
