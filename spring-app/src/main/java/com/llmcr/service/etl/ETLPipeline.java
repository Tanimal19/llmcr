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

        long t0 = System.currentTimeMillis();
        sourceIds.forEach(id -> extractService.extract(id));
        log.info("Extract completed in {} ms", System.currentTimeMillis() - t0);

        // split and load
        long t1 = System.currentTimeMillis();
        sourceIds.forEach(id -> contextRepository.findAllIdsBySourceId(id)
                .forEach(contextId -> splitService.split(contextId)));
        log.info("Split completed in {} ms", System.currentTimeMillis() - t1);

        long t2 = System.currentTimeMillis();
        contextRepository.findAllUnloadedIds().forEach(id -> loadService.loadContext(id));
        log.info("Load after split completed in {} ms", System.currentTimeMillis() - t2);

        // enrich must be performed on all contexts after splitting, since the
        // enrichment may require the complete set of chunks in a context.
        // long t3 = System.currentTimeMillis();
        // contextRepository.findAllIds().forEach(id -> enrichService.enrich(id));
        // log.info("Enrich completed in {} ms", System.currentTimeMillis() - t3);

        // long t4 = System.currentTimeMillis();
        // contextRepository.findAllUnloadedIds().forEach(id ->
        // loadService.loadContext(id));
        // log.info("Load after enrich completed in {} ms", System.currentTimeMillis() -
        // t4);

        log.info("ETL pipeline finished in {} ms", System.currentTimeMillis() - t0);
    }
}
