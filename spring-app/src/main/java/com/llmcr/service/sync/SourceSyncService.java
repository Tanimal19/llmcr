package com.llmcr.service.sync;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.api.APIServiceException;
import com.llmcr.entity.Chunk;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.Source;
import com.llmcr.entity.TrackRoot;
import com.llmcr.entity.Source.SourceType;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.vectorstore.MyVectorStore;
import com.llmcr.repository.SourceRepository;

/**
 * Service for managing data sources (files)
 */
@Service
public class SourceSyncService {

    private static final Logger logger = LoggerFactory.getLogger(SourceSyncService.class);

    private final TrackRootRepository trackRootRepository;
    private final SourceRepository sourceRepository;
    private final MyVectorStore vectorStore;
    private final SourceSyncService self;

    public record TrackRootPreview(Long id, String path, Boolean isSynced, LocalDateTime lastSyncTime,
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

    private Map<Long, TrackRootPreview> trackRootPreviewCache = new HashMap<>();

    public SourceSyncService(
            TrackRootRepository trackRootRepository,
            SourceRepository sourceRepository,
            MyVectorStore vectorStore,
            @Lazy SourceSyncService self) {
        this.trackRootRepository = trackRootRepository;
        this.sourceRepository = sourceRepository;
        this.vectorStore = vectorStore;
        this.self = self;
    }

    public List<TrackRootPreview> getAllTrackRootPreview() {
        logger.info("[SOURCE_SYNC] previewAll:start");
        try {
            List<TrackRootPreview> previews = trackRootRepository.findAllIds().stream()
                    .map(trackRootId -> self.getTrackRootPreview(trackRootId))
                    .toList();
            logger.info("[SOURCE_SYNC] previewAll:done count={}", previews.size());
            return previews;
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_PREVIEW_LIST_FAILED,
                    "Failed to list track root previews", ex);
        }
    }

    public void syncAllTrackRootSource() {
        logger.info("[SOURCE_SYNC] syncAll:start");
        try {
            trackRootRepository.findAllIds().forEach(trackRootId -> self.syncTrackRootSource(trackRootId));
            logger.info("[SOURCE_SYNC] syncAll:done");
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_ALL_FAILED,
                    "Failed to sync all track roots", ex);
        }
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
            TrackRootPreview trackRootPreview = trackRootPreviewCache.get(trackRootId);
            if (trackRootPreview != null) {
                return trackRootPreview;
            }

            TrackRoot trackRoot = trackRootRepository.findById(trackRootId)
                    .orElseThrow(() -> new APIServiceException(APIServiceException.ErrorCode.INVALID_REQUEST,
                            "TrackRoot not found: " + trackRootId));

            logger.info("[SOURCE_SYNC] preview:start trackRoot={}", trackRoot.getPath());

            List<Source> localSources = getLocalSources(trackRoot);
            List<Source> dbSources = sourceRepository.findAllByTrackRootId(trackRootId);

            Map<String, Source> localSourcesByPath = localSources.stream()
                    .collect(Collectors.toMap(Source::getPath, source -> source));
            Map<String, Source> dbSourcesByPath = dbSources.stream()
                    .collect(Collectors.toMap(Source::getPath, source -> source));

            List<SourcePreview> sourcePreviews = new ArrayList<>();

            boolean hasChanges = false;
            for (Source dbSource : dbSources) {
                Source localSource = localSourcesByPath.get(dbSource.getPath());
                if (localSource == null) {
                    logger.debug("[SOURCE_SYNC] preview:removed path={}", dbSource.getPath());
                    sourcePreviews.add(new SourcePreview(dbSource.getId(), dbSource.getPath(), dbSource.getType(),
                            SyncStatus.REMOVED));
                    hasChanges = true;
                    continue;
                }

                SyncStatus syncStatus = Objects.equals(dbSource.getContentHash(), localSource.getContentHash())
                        ? SyncStatus.SYNCED
                        : SyncStatus.MODIFIED;
                if (syncStatus != SyncStatus.SYNCED) {
                    logger.debug("[SOURCE_SYNC] preview:modified path={}", dbSource.getPath());
                    hasChanges = true;
                }
                sourcePreviews
                        .add(new SourcePreview(dbSource.getId(), dbSource.getPath(), dbSource.getType(), syncStatus));
            }

            for (Source localSource : localSources) {
                if (dbSourcesByPath.containsKey(localSource.getPath())) {
                    continue;
                }

                logger.debug("[SOURCE_SYNC] preview:added path={}", localSource.getPath());
                sourcePreviews.add(new SourcePreview(localSource.getId(), localSource.getPath(), localSource.getType(),
                        SyncStatus.ADDED));
                hasChanges = true;
            }

            trackRootPreview = new TrackRootPreview(trackRoot.getId(), trackRoot.getPath(), !hasChanges,
                    trackRoot.getLastSyncTime(), sourcePreviews);
            trackRootPreviewCache.put(trackRootId, trackRootPreview);

            logger.info("[SOURCE_SYNC] preview:done trackRoot={} sourceCount={} synced={}",
                    trackRoot.getPath(), sourcePreviews.size(), !hasChanges);

            return trackRootPreview;
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_PREVIEW_FAILED,
                    "Failed to generate track root preview", ex);
        }
    }

    private List<Source> getLocalSources(TrackRoot trackRoot) {
        if (trackRoot == null || trackRoot.getPath() == null || trackRoot.getPath().isBlank()) {
            logger.warn("TrackRoot or its path is null/blank: " + trackRoot);
            return List.of();
        }

        Path rootPath = Path.of(trackRoot.getPath());
        if (!Files.exists(rootPath)) {
            logger.warn("TrackRoot path does not exist: " + rootPath);
            return List.of();
        }

        Set<SourceType> configuredTypes = trackRoot.getAllowedSourceTypes();
        if (configuredTypes == null || configuredTypes.isEmpty()) {
            logger.warn("TrackRoot has no allowed source types defined, defaulting to all types.");
            configuredTypes = Set.of(SourceType.values());
        }
        final Set<SourceType> allowedTypes = configuredTypes;

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
                throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_LOCAL_SCAN_FAILED,
                        "Failed to walk track root: " + trackRoot.getPath(), e);
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
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_HASH_FAILED,
                    "Failed to read file for hashing: " + path, e);
        } catch (NoSuchAlgorithmException e) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_HASH_FAILED,
                    "SHA-256 is not available", e);
        }
    }

    /**
     * Use the trackRootSourcesPreviewCache to update source sync status in
     * database.
     * Only ADDED, MODIFIED sources will need to be re-extracted.
     */
    @Transactional
    public void syncTrackRootSource(Long trackRootId) {
        try {
            TrackRoot trackRoot = trackRootRepository.findById(trackRootId)
                    .orElseThrow(() -> new APIServiceException(APIServiceException.ErrorCode.INVALID_REQUEST,
                            "TrackRoot not found: " + trackRootId));

            logger.info("[SOURCE_SYNC] trackRoot:start path={}", trackRoot.getPath());

            TrackRootPreview trackRootPreview = trackRootPreviewCache.get(trackRootId);
            if (trackRootPreview == null) {
                trackRootPreview = getTrackRootPreview(trackRootId);
            }
            if (trackRootPreview.isSynced()) {
                logger.info("[SOURCE_SYNC] trackRoot:skip path={} reason=already_synced", trackRoot.getPath());
                return;
            }

            List<Source> sourcesToRemove = new ArrayList<>();
            for (SourcePreview sourcePreview : trackRootPreview.sources()) {
                SyncStatus syncStatus = sourcePreview.syncStatus();

                switch (syncStatus) {
                    case ADDED -> {
                        Source newSource = new Source(sourcePreview.path(),
                                computeContentHash(Path.of(sourcePreview.path())),
                                sourcePreview.type());
                        trackRoot.addSource(newSource);
                        sourceRepository.save(newSource);
                    }
                    case REMOVED -> {
                        sourcesToRemove.add(requireExistingSource(sourcePreview));
                    }
                    case MODIFIED -> {
                        Source existingSource = requireExistingSource(sourcePreview);
                        existingSource.setContentHash(computeContentHash(Path.of(sourcePreview.path())));
                        existingSource.getContexts().clear();
                        existingSource.setExtracted(false);
                        sourceRepository.save(existingSource);
                    }
                    case SYNCED -> {
                        // no action needed
                    }
                    default ->
                        throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED,
                                "Unhandled sync status: " + syncStatus);
                }
            }

            batchRemoveSources(sourcesToRemove);

            trackRoot.setLastSyncTime(LocalDateTime.now());
            trackRootRepository.save(trackRoot);

            trackRootPreviewCache.remove(trackRootId);
            logger.info("[SOURCE_SYNC] trackRoot:done path={}", trackRoot.getPath());
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED,
                    "Failed to sync track root sources", ex);
        }
    }

    private Source requireExistingSource(SourcePreview preview) {
        Source source = resolveExistingSource(preview);
        if (source == null) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED,
                    "Source not found: " + preview.path());
        }
        return source;
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
            List<Chunk> chunks = sources.stream()
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
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_REMOVE_CHUNKS_FAILED,
                    "Failed to remove chunks from vector store", ex);
        }
    }
}
