package com.llmcr.feature.sync;

import com.llmcr.domain.entity.Source;
import com.llmcr.domain.entity.Source.SourceType;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.repository.TrackRootRepository;
import com.llmcr.domain.sse.SseTaskObject;
import com.llmcr.domain.sse.SseTaskObject.SseTaskProgress;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing data sources (files).
 */
@Service
public class SourceSyncService {

    private static final Logger logger = LoggerFactory.getLogger(SourceSyncService.class);

    private final TrackRootRepository trackRootRepository;
    private final SourcePreviewService sourcePreviewService;
    private final SourceChangeApplier sourceChangeApplier;

    public record TrackRootPreview(
            Long id,
            String path,
            Boolean isSynced,
            LocalDateTime lastSyncTime,
            List<SourcePreview> sources) {
    }

    public record SourcePreview(Long id, String path, SourceType type, SyncStatus syncStatus) {
    }

    public enum SyncStatus {
        SYNCED,
        REMOVED,
        MODIFIED,
        ADDED,
    }

    public SourceSyncService(
            TrackRootRepository trackRootRepository,
            SourcePreviewService sourcePreviewService,
            SourceChangeApplier sourceChangeApplier) {
        this.trackRootRepository = trackRootRepository;
        this.sourcePreviewService = sourcePreviewService;
        this.sourceChangeApplier = sourceChangeApplier;
    }

    public String getLastAllSyncTime() {
        try {
            LocalDateTime lastAllSyncTime = trackRootRepository
                    .findAll()
                    .stream()
                    .map(TrackRoot::getLastSyncTime)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            logger.info("Getting last sync time for all track roots, lastAllSyncTime={}", lastAllSyncTime);
            return lastAllSyncTime == null ? "Never" : lastAllSyncTime.toString();
        } catch (Exception ex) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.SOURCE_SYNC_GET_SYNC_TIME_FAILED,
                    "Failed to get last sync time",
                    ex);
        }
    }

    public List<TrackRootPreview> getAllTrackRootPreview() {
        return sourcePreviewService.getAllTrackRootPreview();
    }

    public void syncAllTrackRootSource() {
        syncAllTrackRootSource(null, () -> false);
    }

    public void syncAllTrackRootSource(
            Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested) {
        trackRootRepository
                .findAllIds()
                .forEach(trackRootId -> syncTrackRootSource(trackRootId, progressListener, cancellationRequested));
    }

    /**
     * Use cached track root preview to update source sync status in database.
     * Only ADDED and MODIFIED sources will need to be re-extracted.
     */
    @Transactional
    public void syncTrackRootSource(
            Long trackRootId,
            Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested) {
        try {
            SseTaskObject.throwIfCancelled(cancellationRequested);
            SseTaskObject.emitProgress(progressListener, "SYNC", "Syncing track root: " + trackRootId);

            TrackRoot trackRoot = trackRootRepository.findById(trackRootId).orElseThrow();
            TrackRootPreview trackRootPreview = sourcePreviewService.getOrCreateTrackRootPreview(trackRootId);
            if (trackRootPreview.isSynced()) {
                markTrackRootSyncedAndSkip(trackRoot, progressListener);
                return;
            }

            List<Source> sourcesToRemove = processSourceChanges(trackRoot, trackRootPreview, cancellationRequested);

            SseTaskObject.throwIfCancelled(cancellationRequested);
            SseTaskObject.emitProgress(
                    progressListener,
                    "SYNC",
                    "Removing " + sourcesToRemove.size() + " sources for track root: " + trackRoot.getPath());
            sourceChangeApplier.batchRemoveSources(sourcesToRemove);

            trackRoot.setLastSyncTime(LocalDateTime.now());
            trackRootRepository.save(trackRoot);
            sourcePreviewService.evictTrackRootPreview(trackRootId);

            SseTaskObject.throwIfCancelled(cancellationRequested);
            SseTaskObject.emitProgress(progressListener, "SYNC",
                    "Completed syncing track root: " + trackRoot.getPath());
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED, ex);
        }
    }

    private void markTrackRootSyncedAndSkip(TrackRoot trackRoot, Consumer<SseTaskProgress> progressListener) {
        SseTaskObject.emitProgress(progressListener, "SYNC",
                "Track root already synced, skipping: " + trackRoot.getPath());
        trackRoot.setLastSyncTime(LocalDateTime.now());
        trackRootRepository.save(trackRoot);
    }

    private List<Source> processSourceChanges(
            TrackRoot trackRoot,
            TrackRootPreview trackRootPreview,
            BooleanSupplier cancellationRequested) {
        List<Source> sourcesToRemove = new ArrayList<>();
        for (SourcePreview sourcePreview : trackRootPreview.sources()) {
            SseTaskObject.throwIfCancelled(cancellationRequested);
            Source sourceToRemove = sourceChangeApplier.applySourceSyncStatus(trackRoot, sourcePreview);
            if (sourceToRemove != null) {
                sourcesToRemove.add(sourceToRemove);
            }
        }
        return sourcesToRemove;
    }
}
