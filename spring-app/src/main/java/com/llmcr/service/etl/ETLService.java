package com.llmcr.service.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.llmcr.api.APIServiceException;
import com.llmcr.repository.ContextRepository;
import com.llmcr.repository.SourceRepository;

@Service
public class ETLService {

    private static final Logger logger = LoggerFactory.getLogger(ETLService.class);

    private final SourceRepository sourceRepository;
    private final ContextRepository contextRepository;
    private final ExtractService extractService;
    private final SplitService splitService;
    private final EnrichService enrichService;
    private final LoadService loadService;

    public ETLService(
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
        logger.info("ETL pipeline started");

        long pipelineStart = System.currentTimeMillis();
        try {
            long t0 = System.currentTimeMillis();
            try {
                sourceRepository.findAllUnextractedIds().forEach(id -> extractService.extract(id));
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.ETL_EXTRACT_FAILED,
                        "ETL extract stage failed", ex);
            }
            logger.info("Extract completed in {} ms", System.currentTimeMillis() - t0);

            // split and load
            long t1 = System.currentTimeMillis();
            try {
                contextRepository.findAllUnsplittedIds().forEach(id -> splitService.split(id));
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.ETL_SPLIT_FAILED, "ETL split stage failed",
                        ex);
            }
            logger.info("Split completed in {} ms", System.currentTimeMillis() - t1);

            long t2 = System.currentTimeMillis();
            try {
                contextRepository.findAllUnloadedIds().forEach(id -> loadService.loadContextChunks(id));
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.ETL_LOAD_FAILED,
                        "ETL load-after-split stage failed", ex);
            }
            logger.info("Load after split completed in {} ms", System.currentTimeMillis() - t2);

            // enrich must be performed on all contexts after splitting, since the
            // enrichment may require the complete set of chunks in a context.
            long t3 = System.currentTimeMillis();
            try {
                contextRepository.findAllUnenrichedIds().forEach(id -> enrichService.enrich(id));
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.ETL_ENRICH_FAILED,
                        "ETL enrich stage failed", ex);
            }
            logger.info("Enrich completed in {} ms", System.currentTimeMillis() - t3);

            long t4 = System.currentTimeMillis();
            try {
                contextRepository.findAllUnloadedIds().forEach(id -> loadService.loadContextChunks(id));
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.ETL_LOAD_FAILED,
                        "ETL load-after-enrich stage failed", ex);
            }
            logger.info("Load after enrich completed in {} ms", System.currentTimeMillis() - t4);

            logger.info("ETL pipeline finished in {} ms", System.currentTimeMillis() - pipelineStart);
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.ETL_RUN_FAILED, "ETL pipeline execution failed",
                    ex);
        }
    }
}
