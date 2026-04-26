package com.llmcr.service.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.llmcr.entity.Context.ContextType;
import com.llmcr.repository.ContextRepository;
import com.llmcr.repository.SourceRepository;

@Service
public class ETLPipeline {

    private static final Logger log = LoggerFactory.getLogger(ETLPipeline.class);

    private final SourceRepository sourceRepository;
    private final ContextRepository contextRepository;
    private final ExtractService extractService;
    private final SplitService splitService;
    private final EnrichService enrichService;
    private final LoadService loadService;

    public ETLPipeline(
            SourceRepository sourceRepository,
            ContextRepository contextRepository,
            ExtractService extractService,
            SplitService splitService,
            EnrichService enrichService,
            LoadService loadService) {
        this.sourceRepository = sourceRepository;
        this.contextRepository = contextRepository;
        this.extractService = extractService;
        this.splitService = splitService;
        this.enrichService = enrichService;
        this.loadService = loadService;
    }

    public void run() {
        log.info("ETL pipeline started.");

        loadService.initCollections();
        sourceRepository.findAllIds().forEach(id -> extractService.extract(id));
        contextRepository.findAllIds().forEach(id -> splitService.splitAndLoad(id));
        contextRepository.findAllIdsByType(ContextType.CLASSNODE)
                .forEach(id -> enrichService.enrichAndLoad(id));
    }
}
