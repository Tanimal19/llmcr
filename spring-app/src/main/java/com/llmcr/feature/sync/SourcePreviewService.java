package com.llmcr.feature.sync;

import com.llmcr.domain.entity.Source;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.repository.SourceRepository;
import com.llmcr.domain.repository.TrackRootRepository;
import com.llmcr.feature.sync.SourceSyncService.SourcePreview;
import com.llmcr.feature.sync.SourceSyncService.SyncStatus;
import com.llmcr.feature.sync.SourceSyncService.TrackRootPreview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourcePreviewService {

    private static final Logger logger = LoggerFactory.getLogger(SourcePreviewService.class);

    private final TrackRootRepository trackRootRepository;
    private final SourceRepository sourceRepository;
    private final LocalSourceScanner localSourceScanner;
    private final Map<Long, TrackRootPreview> trackRootPreviewCache = new ConcurrentHashMap<>();

    private record PreviewComputation(List<SourcePreview> sourcePreviews, boolean hasChanges) {
    }

    public SourcePreviewService(
            TrackRootRepository trackRootRepository,
            SourceRepository sourceRepository,
            LocalSourceScanner localSourceScanner) {
        this.trackRootRepository = trackRootRepository;
        this.sourceRepository = sourceRepository;
        this.localSourceScanner = localSourceScanner;
    }

    @Transactional
    public List<TrackRootPreview> getAllTrackRootPreview() {
        logger.info("previewAll:start");
        try {
            List<TrackRootPreview> previews = trackRootRepository
                    .findAllIds()
                    .stream()
                    .map(this::getTrackRootPreview)
                    .toList();
            logger.info("previewAll:done count={}", previews.size());
            return previews;
        } catch (Exception ex) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.SOURCE_SYNC_PREVIEW_LIST_FAILED,
                    "Failed to list track root previews",
                    ex);
        }
    }

    @Transactional
    public TrackRootPreview getTrackRootPreview(Long trackRootId) {
        try {
            TrackRoot trackRoot = loadTrackRoot(trackRootId);

            logger.info("preview:start trackRoot={}", trackRoot.getPath());

            List<Source> localSources = localSourceScanner.getLocalSources(trackRoot);
            List<Source> dbSources = sourceRepository.findAllByTrackRootId(trackRootId);

            PreviewComputation previewComputation = computePreview(localSources, dbSources);

            TrackRootPreview trackRootPreview = new TrackRootPreview(
                    trackRoot.getId(),
                    trackRoot.getPath(),
                    !previewComputation.hasChanges(),
                    trackRoot.getLastSyncTime(),
                    previewComputation.sourcePreviews());
            trackRootPreviewCache.put(trackRootId, trackRootPreview);

            logger.info(
                    "preview:done trackRoot={} sourceCount={} synced={}",
                    trackRoot.getPath(),
                    previewComputation.sourcePreviews().size(),
                    !previewComputation.hasChanges());

            return trackRootPreview;
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_PREVIEW_FAILED, ex);
        }
    }

    @Transactional
    public TrackRootPreview getOrCreateTrackRootPreview(Long trackRootId) {
        TrackRootPreview cached = trackRootPreviewCache.get(trackRootId);
        if (cached != null) {
            return cached;
        }
        return getTrackRootPreview(trackRootId);
    }

    public void evictTrackRootPreview(Long trackRootId) {
        trackRootPreviewCache.remove(trackRootId);
    }

    private TrackRoot loadTrackRoot(Long trackRootId) {
        return trackRootRepository
                .findById(trackRootId)
                .orElseThrow(() -> new APIServiceException(
                        APIServiceException.ErrorCode.INVALID_REQUEST,
                        "TrackRoot not found: " + trackRootId));
    }

    private PreviewComputation computePreview(List<Source> localSources, List<Source> dbSources) {
        Map<String, Source> localSourcesByPath = localSources
                .stream()
                .collect(Collectors.toMap(Source::getPath, source -> source));
        Map<String, Source> dbSourcesByPath = dbSources
                .stream()
                .collect(Collectors.toMap(Source::getPath, source -> source));

        List<SourcePreview> sourcePreviews = new ArrayList<>();
        boolean hasChanges = collectDbSourcePreviews(dbSources, localSourcesByPath, sourcePreviews);
        hasChanges |= collectAddedSourcePreviews(localSources, dbSourcesByPath, sourcePreviews);

        return new PreviewComputation(sourcePreviews, hasChanges);
    }

    private boolean collectDbSourcePreviews(
            List<Source> dbSources,
            Map<String, Source> localSourcesByPath,
            List<SourcePreview> sourcePreviews) {
        boolean hasChanges = false;
        for (Source dbSource : dbSources) {
            Source localSource = localSourcesByPath.get(dbSource.getPath());
            if (localSource == null) {
                logger.debug("preview:removed path={}", dbSource.getPath());
                sourcePreviews.add(new SourcePreview(
                        dbSource.getId(),
                        dbSource.getPath(),
                        dbSource.getType(),
                        SyncStatus.REMOVED));
                hasChanges = true;
                continue;
            }

            SyncStatus syncStatus = Objects.equals(dbSource.getContentHash(), localSource.getContentHash())
                    ? SyncStatus.SYNCED
                    : SyncStatus.MODIFIED;
            if (syncStatus != SyncStatus.SYNCED) {
                logger.debug("preview:modified path={}", dbSource.getPath());
                hasChanges = true;
            }
            sourcePreviews.add(new SourcePreview(dbSource.getId(), dbSource.getPath(), dbSource.getType(), syncStatus));
        }
        return hasChanges;
    }

    private boolean collectAddedSourcePreviews(
            List<Source> localSources,
            Map<String, Source> dbSourcesByPath,
            List<SourcePreview> sourcePreviews) {
        boolean hasChanges = false;
        for (Source localSource : localSources) {
            if (dbSourcesByPath.containsKey(localSource.getPath())) {
                continue;
            }

            logger.debug("preview:added path={}", localSource.getPath());
            sourcePreviews.add(new SourcePreview(localSource.getId(), localSource.getPath(), localSource.getType(),
                    SyncStatus.ADDED));
            hasChanges = true;
        }
        return hasChanges;
    }
}
