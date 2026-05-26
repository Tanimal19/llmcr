package com.llmcr.feature.sync;

import com.llmcr.domain.entity.Source.SourceType;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.repository.SourceRepository;
import com.llmcr.domain.repository.TrackRootRepository;
import com.llmcr.domain.sse.VoidSseTaskObject;
import com.llmcr.feature.sync.etl.ETLService;
import com.llmcr.infrastructure.vectorstore.MyVectorStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing data sources (files)
 */
@Service
public class SourceSyncService extends VoidSseTaskObject {

    private static final Logger logger = LoggerFactory.getLogger(SourceSyncService.class);

    private final TrackRootRepository trackRootRepository;
    private final SourceRepository sourceRepository;
    private final MyVectorStore vectorStore;
    private final ETLService etlService;
    private final SourceSyncService self;

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

    private final Map<Long, TrackRootPreview> trackRootPreviewCache = new HashMap<>();

    private record PreviewComputation(List<SourcePreview> sourcePreviews, boolean hasChanges) {
    }

    public SourceSyncService(
            TrackRootRepository trackRootRepository,
            SourceRepository sourceRepository,
            MyVectorStore vectorStore,
            ETLService etlService,
            @Lazy SourceSyncService self) {
        this.trackRootRepository = trackRootRepository;
        this.sourceRepository = sourceRepository;
        this.vectorStore = vectorStore;
        this.etlService = etlService;
        this.self = self;
    }

    public String getTaskName() {
        return "SourceSync";
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
        logger.info("previewAll:start");
        try {
            List<TrackRootPreview> previews = trackRootRepository
                    .findAllIds()
                    .stream()
                    .map(trackRootId -> self.getTrackRootPreview(trackRootId))
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

    public void syncAllTrackRootSource() {
        self.execute(null, null, () -> false);
    }

    @Override
    public Void execute(
            Void input,
            Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested) {
        try {
            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "SYNC", "Starting to sync all track roots");
            syncAllTrackRoots(progressListener, cancellationRequested);

            emitProgress(progressListener, "SYNC", "Completed syncing all track roots");

            etlService.execute(progressListener, cancellationRequested);
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_FAILED, ex);
        }
        return null;
    }

    /**
     * Return list of sources under the given trackroot with updated sync status.
     * This will not update database and is mainly used for previewing sync status
     * in frontend.
     *
     * The sync status is determined by following algorithm:
     * 1. If a source in database no longer exists locally, mark it as REMOVED.
     * 2. If a source is not currently in database but exists locally, create a new
     * source and mark it as ADDED.
     * 3. If a source exists in both database and local, but content hash is
     * different, mark it as MODIFIED. Otherwise, mark it as SYNCED.
     */
    @Transactional
    public TrackRootPreview getTrackRootPreview(Long trackRootId) {
        try {
            TrackRoot trackRoot = loadTrackRoot(trackRootId);

            logger.info("preview:start trackRoot={}", trackRoot.getPath());

            List<Source> localSources = getLocalSources(trackRoot);
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

    private void syncAllTrackRoots(
            Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested) {
        trackRootRepository
                .findAllIds()
                .forEach(trackRootId -> self.syncTrackRootSource(trackRootId, progressListener, cancellationRequested));
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
                sourcePreviews.add(new SourcePreview(dbSource.getId(), dbSource.getPath(), dbSource.getType(),
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

    private List<Source> getLocalSources(TrackRoot trackRoot) {
        Path rootPath = resolveTrackRootPath(trackRoot);
        if (rootPath == null) {
            return List.of();
        }

        Set<SourceType> allowedTypes = resolveAllowedSourceTypes(trackRoot);
        return scanLocalSources(trackRoot, rootPath, allowedTypes);
    }

    private Path resolveTrackRootPath(TrackRoot trackRoot) {
        if (trackRoot == null || trackRoot.getPath() == null || trackRoot.getPath().isBlank()) {
            logger.warn("TrackRoot or its path is null/blank: " + trackRoot);
            return null;
        }

        Path rootPath = Path.of(trackRoot.getPath());
        if (!Files.exists(rootPath)) {
            logger.warn("TrackRoot path does not exist: " + rootPath);
            return null;
        }

        return rootPath;
    }

    private Set<SourceType> resolveAllowedSourceTypes(TrackRoot trackRoot) {
        Set<SourceType> configuredTypes = trackRoot.getAllowedSourceTypes();
        if (configuredTypes == null || configuredTypes.isEmpty()) {
            logger.warn("TrackRoot has no allowed source types defined, defaulting to all types.");
            return Set.of(SourceType.values());
        }
        return configuredTypes;
    }

    private List<Source> scanLocalSources(TrackRoot trackRoot, Path rootPath, Set<SourceType> allowedTypes) {
        List<Source> sources = new ArrayList<>();
        if (Files.isRegularFile(rootPath)) {
            Source source = createSource(rootPath, allowedTypes);
            if (source != null) {
                sources.add(source);
            }
            return sources;
        }

        if (Files.isDirectory(rootPath)) {
            try (Stream<Path> pathStream = Files.walk(rootPath)) {
                pathStream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(Path::toString))
                        .map(path -> createSource(path, allowedTypes))
                        .filter(Objects::nonNull)
                        .forEach(sources::add);
            } catch (IOException e) {
                throw new APIServiceException(
                        APIServiceException.ErrorCode.SOURCE_SYNC_LOCAL_SCAN_FAILED,
                        "Failed to walk track root: " + trackRoot.getPath(),
                        e);
            }
            return sources;
        }

        logger.warn("TrackRoot path is not a file or directory: " + rootPath);
        return List.of();
    }

    private Source createSource(Path path, Set<SourceType> allowedTypes) {
        SourceType sourceType = resolveSourceType(path);
        if (sourceType == null) {
            logger.debug("Unrecognized file type for source, Dropped: " + path);
            return null;
        }

        if (!allowedTypes.contains(sourceType)) {
            logger.debug("Source type not allowed by track root config, Dropped: " + path);
            return null;
        }

        return new Source(path.toString(), computeContentHash(path), sourceType);
    }

    private SourceType resolveSourceType(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".java")) {
            return Source.SourceType.JAVACODE;
        }
        if (fileName.endsWith(".pdf")) {
            return Source.SourceType.PDF;
        }
        if (fileName.endsWith(".md") || fileName.endsWith(".markdown")) {
            return Source.SourceType.MARKDOWN;
        }
        if (fileName.endsWith(".adoc") || fileName.endsWith(".asciidoc")) {
            return Source.SourceType.ASCIIDOC;
        }
        if (fileName.endsWith(".json") || fileName.endsWith(".jsonl")) {
            return Source.SourceType.JSON;
        }

        return null;
    }

    private static String computeContentHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();

            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (IOException e) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.SOURCE_SYNC_HASH_FAILED,
                    "Failed to read file for hashing: " + path,
                    e);
        } catch (NoSuchAlgorithmException e) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_HASH_FAILED, e);
        }
    }

    /**
     * Use the trackRootSourcesPreviewCache to update source sync status in
     * database.
     * Only ADDED, MODIFIED sources will need to be re-extracted.
     */
    @Transactional
    public void syncTrackRootSource(
            Long trackRootId,
            Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested) {
        try {
            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "SYNC", "Syncing track root: " + trackRootId);

            TrackRoot trackRoot = trackRootRepository.findById(trackRootId).orElseThrow();
            TrackRootPreview trackRootPreview = getOrCreateTrackRootPreview(trackRootId);
            if (trackRootPreview.isSynced()) {
                markTrackRootSyncedAndSkip(trackRoot, progressListener);
                return;
            }

            List<Source> sourcesToRemove = processSourceChanges(trackRoot, trackRootPreview, cancellationRequested);

            throwIfCancelled(cancellationRequested);
            emitProgress(
                    progressListener,
                    "SYNC",
                    "Removing " + sourcesToRemove.size() + " sources for track root: " + trackRoot.getPath());
            batchRemoveSources(sourcesToRemove);

            trackRoot.setLastSyncTime(LocalDateTime.now());
            trackRootRepository.save(trackRoot);
            trackRootPreviewCache.remove(trackRootId);

            throwIfCancelled(cancellationRequested);
            emitProgress(progressListener, "SYNC", "Completed syncing track root: " + trackRoot.getPath());
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED, ex);
        }
    }

    private Source requireExistingSource(SourcePreview preview) {
        Source source = resolveExistingSource(preview);
        if (source == null) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED,
                    "Source not found: " + preview.path());
        }
        return source;
    }

    private TrackRootPreview getOrCreateTrackRootPreview(Long trackRootId) {
        TrackRootPreview trackRootPreview = trackRootPreviewCache.get(trackRootId);
        if (trackRootPreview != null) {
            return trackRootPreview;
        }
        return getTrackRootPreview(trackRootId);
    }

    private void markTrackRootSyncedAndSkip(TrackRoot trackRoot, Consumer<SseTaskProgress> progressListener) {
        emitProgress(progressListener, "SYNC", "Track root already synced, skipping: " + trackRoot.getPath());
        trackRoot.setLastSyncTime(LocalDateTime.now());
        trackRootRepository.save(trackRoot);
    }

    private List<Source> processSourceChanges(
            TrackRoot trackRoot,
            TrackRootPreview trackRootPreview,
            BooleanSupplier cancellationRequested) {
        List<Source> sourcesToRemove = new ArrayList<>();
        for (SourcePreview sourcePreview : trackRootPreview.sources()) {
            throwIfCancelled(cancellationRequested);
            applySourceSyncStatus(trackRoot, sourcePreview, sourcesToRemove);
        }
        return sourcesToRemove;
    }

    private void applySourceSyncStatus(
            TrackRoot trackRoot,
            SourcePreview sourcePreview,
            List<Source> sourcesToRemove) {
        SyncStatus syncStatus = sourcePreview.syncStatus();
        switch (syncStatus) {
            case ADDED -> addSource(trackRoot, sourcePreview);
            case REMOVED -> sourcesToRemove.add(requireExistingSource(sourcePreview));
            case MODIFIED -> updateModifiedSource(sourcePreview);
            case SYNCED -> {
                // no action needed
            }
            default -> throw new APIServiceException(
                    APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED,
                    "Unhandled sync status: " + syncStatus);
        }
    }

    private void addSource(TrackRoot trackRoot, SourcePreview sourcePreview) {
        Source newSource = new Source(
                sourcePreview.path(),
                computeContentHash(Path.of(sourcePreview.path())),
                sourcePreview.type());
        trackRoot.addSource(newSource);
        sourceRepository.save(newSource);
    }

    private void updateModifiedSource(SourcePreview sourcePreview) {
        Source existingSource = requireExistingSource(sourcePreview);
        existingSource.setContentHash(computeContentHash(Path.of(sourcePreview.path())));
        existingSource.getContexts().clear();
        existingSource.setExtracted(false);
        sourceRepository.save(existingSource);
    }

    private Source resolveExistingSource(SourcePreview preview) {
        if (preview.id() != null) {
            return sourceRepository.findById(preview.id()).orElse(null);
        }
        return sourceRepository.findByPath(preview.path());
    }

    private void batchRemoveSources(List<Source> sources) {
        if (sources.isEmpty()) {
            return;
        }
        batchRemoveSourceChunks(sources);
        sourceRepository.deleteAll(sources);
    }

    private void batchRemoveSourceChunks(List<Source> sources) {
        try {
            List<Chunk> chunks = sources
                    .stream()
                    .flatMap(source -> source.getContexts().stream())
                    .flatMap(context -> context.getChunks().stream())
                    .toList();

            if (chunks.isEmpty()) {
                return;
            }

            Map<String, Set<Long>> collectionToChunkIds = new HashMap<>();
            for (Chunk chunk : chunks) {
                Long chunkId = chunk.getId();
                if (chunkId == null) {
                    continue;
                }

                for (ChunkCollection chunkCollection : chunk.getChunkCollections()) {
                    String collectionName = chunkCollection.getName();
                    if (collectionName == null) {
                        continue;
                    }
                    collectionToChunkIds.computeIfAbsent(collectionName, key -> new HashSet<>()).add(chunkId);
                }
            }

            for (Map.Entry<String, Set<Long>> entry : collectionToChunkIds.entrySet()) {
                vectorStore.removeChunks(new ArrayList<>(entry.getValue()), entry.getKey());
            }

            for (Chunk chunk : chunks) {
                for (ChunkCollection chunkCollection : new ArrayList<>(chunk.getChunkCollections())) {
                    chunkCollection.removeChunk(chunk);
                }
            }
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_REMOVE_CHUNKS_FAILED, ex);
        }
    }
}
