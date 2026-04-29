package com.llmcr.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.llmcr.repository.SourceRepository;
import com.llmcr.repository.TrackRootRepository;

@Component
public class SyncService {
    private final TrackRootRepository trackRootRepository;
    private final SourceRepository sourceRepository;
    private final SourceService sourceService;

    public SyncService(TrackRootRepository trackRootRepository, SourceRepository sourceRepository,
            SourceService sourceService) {
        this.trackRootRepository = trackRootRepository;
        this.sourceRepository = sourceRepository;
        this.sourceService = sourceService;
    }

    /**
     * 1. update all trackroots.
     * - Remove database sources that no longer exist locally.
     * - Add new local sources that are not in database.
     * 2. update all sources
     */
    public void sync() {
        trackRootRepository.findAllIds().forEach(sourceService::updateTrackRootSources);
        LocalDateTime syncTime = LocalDateTime.now();
        sourceRepository.findAllIds().forEach(id -> sourceService.updateSourceSyncStatus(id, syncTime));
    }
}
