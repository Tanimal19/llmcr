package com.llmcr.feature.sync.source;

import java.time.LocalDateTime;
import java.util.List;

import com.llmcr.domain.entity.Source.SourceType;

public record TrackRootPreview(
        Long id,
        String path,
        Boolean isSynced,
        LocalDateTime lastSyncTime,
        List<SourcePreview> sources) {

    public record SourcePreview(Long id, String path, SourceType type, SyncStatus syncStatus) {
    }

    public enum SyncStatus {
        SYNCED,
        REMOVED,
        MODIFIED,
        ADDED,
    }
}
