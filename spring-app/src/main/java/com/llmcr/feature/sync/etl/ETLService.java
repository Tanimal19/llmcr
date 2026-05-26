package com.llmcr.feature.sync.etl;

import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.repository.ContextRepository;
import com.llmcr.domain.repository.SourceRepository;
import com.llmcr.domain.sse.VoidSseTaskObject;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class ETLService extends VoidSseTaskObject {

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

    public String getTaskName() {
        return "Extract-Split-Load";
    }

    public void execute(Consumer<SseTaskProgress> progressListener, BooleanSupplier cancellationRequested) {
        throwIfCancelled(cancellationRequested);
        emitProgress(progressListener, "ETL", "Starting ETL pipeline");

        long pipelineStart = System.currentTimeMillis();
        try {
            try {
                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, "ETL", "Running extract stage");
                long t0 = System.currentTimeMillis();
                sourceRepository.findAllUnextractedIds().forEach(id -> extractService.extract(id));
                emitProgress(
                        progressListener,
                        "ETL",
                        "Extract stage completed in " + (System.currentTimeMillis() - t0) + " ms");
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.ETL_EXTRACT_FAILED, ex);
            }

            // split and load
            try {
                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, "ETL", "Running split stage");
                long t1 = System.currentTimeMillis();
                contextRepository.findAllUnsplittedIds().forEach(id -> splitService.split(id));
                emitProgress(
                        progressListener,
                        "ETL",
                        "Split stage completed in " + (System.currentTimeMillis() - t1) + " ms");
            } catch (Exception ex) {
                throw new APIServiceException(APIServiceException.ErrorCode.ETL_SPLIT_FAILED, ex);
            }

            try {
                throwIfCancelled(cancellationRequested);
                emitProgress(progressListener, "ETL", "Running load stage after split");
                long t2 = System.currentTimeMillis();
                contextRepository.findAllUnloadedIds().forEach(id -> loadService.loadContext(id));
                emitProgress(
                        progressListener,
                        "ETL",
                        "Load after split stage completed in " + (System.currentTimeMillis() - t2) + " ms");
            } catch (Exception ex) {
                throw new APIServiceException(
                        APIServiceException.ErrorCode.ETL_LOAD_FAILED,
                        "ETL load-after-split stage failed",
                        ex);
            }

            // enrich must be performed on all contexts after splitting, since the
            // enrichment may require the complete set of chunks in a context.
            // try {
            // throwIfCancelled(cancellationRequested);
            // emitProgress(progressListener, "ETL", "Running enrich stage");
            // long t3 = System.currentTimeMillis();
            // contextRepository.findAllUnenrichedIds().forEach(id ->
            // enrichService.enrich(id));
            // emitProgress(progressListener, "ETL",
            // "Enrich stage completed in " + (System.currentTimeMillis() - t3) + " ms");
            // } catch (Exception ex) {
            // throw new
            // APIServiceException(APIServiceException.ErrorCode.ETL_ENRICH_FAILED, ex);
            // }

            // try {
            // throwIfCancelled(cancellationRequested);
            // emitProgress(progressListener, "ETL", "Running load stage after enrich");
            // long t4 = System.currentTimeMillis();
            // contextRepository.findAllUnloadedIds().forEach(id ->
            // loadService.loadContextChunks(id));
            // emitProgress(progressListener, "ETL",
            // "Load after enrich stage completed in " + (System.currentTimeMillis() - t4) +
            // " ms");
            // } catch (Exception ex) {
            // throw new APIServiceException(APIServiceException.ErrorCode.ETL_LOAD_FAILED,
            // "ETL load-after-enrich stage failed", ex);
            // }

            emitProgress(
                    progressListener,
                    "ETL",
                    "ETL pipeline completed in " + (System.currentTimeMillis() - pipelineStart) + " ms");
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.ETL_RUN_FAILED,
                    "ETL pipeline execution failed",
                    ex);
        }
    }
}
