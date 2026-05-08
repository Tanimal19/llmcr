package com.llmcr.service.sync;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.llmcr.repository.SourceRepository;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.service.etl.ETLPipeline;

@Service
public class SyncService {
    private final TrackRootRepository trackRootRepository;
    private final SourceRepository sourceRepository;
    private final SourceService sourceService;
    private final ETLPipeline etlPipeline;

    public SyncService(TrackRootRepository trackRootRepository, SourceRepository sourceRepository,
            SourceService sourceService, ETLPipeline etlPipeline) {
        this.trackRootRepository = trackRootRepository;
        this.sourceRepository = sourceRepository;
        this.sourceService = sourceService;
        this.etlPipeline = etlPipeline;
    }

    /**
     * 1. update all trackroots.
     * - Remove database sources that no longer exist locally.
     * - Add new local sources that are not in database.
     * 2. update all sources
     * 3. re-run ETL to update chunks and contexts
     */
    public void sync() {
        trackRootRepository.findAllIds().forEach(sourceService::updateTrackRootSources);
        LocalDateTime syncTime = LocalDateTime.now();
        sourceRepository.findAllIds().forEach(id -> sourceService.updateSourceSyncStatus(id, syncTime));
        etlPipeline.run();
    }
}
