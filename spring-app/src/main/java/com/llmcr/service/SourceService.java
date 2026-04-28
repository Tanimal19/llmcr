package com.llmcr.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Source;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.service.etl.ETLPipeline;
import com.llmcr.vectorstore.MyVectorStore;
import com.llmcr.repository.SourceRepository;

/**
 * Service for managing data sources (files)
 */
@Service
public class SourceService {

    private static final Logger logger = LoggerFactory.getLogger(SourceService.class);

    private final TrackRootRepository trackRootRepository;
    private final SourceRepository sourceRepository;
    private final MyVectorStore vectorStore;
    private final ETLPipeline etlPipeline;

    public SourceService(TrackRootRepository trackRootRepository, SourceRepository sourceRepository,
            MyVectorStore vectorStore, ETLPipeline etlPipeline) {
        this.trackRootRepository = trackRootRepository;
        this.sourceRepository = sourceRepository;
        this.vectorStore = vectorStore;
        this.etlPipeline = etlPipeline;
    }

    /**
     * 1. Reconcile local files with database records.
     * - Remove database sources that no longer exist locally.
     * - Add new local sources that are not in database.
     * 2. Recompute content hash and update last sync time.
     * 3. Run ETL pipeline for all sources. (The ETL pipeline will internally check
     * the sync status of each source and skip if already processed)
     */
    public void refreshTrackRoots() {
        trackRootRepository.findAll().forEach(this::reconcileSource);
        LocalDateTime syncTime = LocalDateTime.now();
        sourceRepository.findAll().forEach(source -> updateSourceSyncStatus(source, syncTime));

        // run ETL for sources that are unsynced
        List<Long> unextractedIds = sourceRepository.findAllIds();
        etlPipeline.run(unextractedIds);
    }

    @Transactional
    private void reconcileSource(TrackRoot trackRoot) {
        List<Source> localSources = loadSources(trackRoot);
        List<Source> dbSources = sourceRepository.findAll().stream()
                .filter(source -> Objects.equals(source.getTrackRoot(), trackRoot))
                .toList();

        // remove db sources that no longer exist locally
        Set<String> localPaths = localSources.stream()
                .map(Source::getPath)
                .collect(Collectors.toSet());
        for (Source dbSource : dbSources) {
            if (!localPaths.contains(dbSource.getPath())) {
                logger.info("Source no longer exists locally, removing: " + dbSource.getPath());
                removeSource(dbSource);
            }
        }

        for (Source localSource : localSources) {
            Source existing = sourceRepository.findByPath(localSource.getPath());

            // insert new source
            if (existing == null) {
                localSource.setTrackRoot(trackRoot);
                trackRoot.addSource(localSource);
                continue;
            }

            // update existing source if track root changed
            if (!Objects.equals(existing.getTrackRoot(), trackRoot)) {
                existing.setTrackRoot(trackRoot);
            }
        }

        trackRootRepository.save(trackRoot);
    }

    @Transactional
    private void updateSourceSyncStatus(Source source, LocalDateTime syncTime) {
        Path sourcePath = Path.of(source.getPath());
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            logger.warn("Source path does not exist or is not a regular file, remove source: " + sourcePath);
            removeSource(source);
            return;
        }

        String currentHash = computeContentHash(sourcePath);
        if (!Objects.equals(source.getContentHash(), currentHash)) {
            logger.info("Source content changed: " + sourcePath);
            source.setContentHash(currentHash);
        }
        source.setLastSyncTime(syncTime);
        sourceRepository.save(source);
    }

    @Transactional
    private void removeSource(Source source) {
        // remove chunks from vector store before deleting source and contexts
        List<Long> affectedChunkIds = source.getContexts().stream()
                .flatMap(context -> context.getChunks().stream())
                .map(chunk -> chunk.getId())
                .toList();
        List<String> affectedCollectionNames = source.getContexts().stream()
                .flatMap(context -> context.getChunks().stream())
                .flatMap(chunk -> chunk.getChunkCollections().stream())
                .map(collection -> collection.getName())
                .distinct()
                .toList();
        for (String collectionName : affectedCollectionNames) {
            vectorStore.removeChunks(affectedChunkIds, collectionName);
        }

        sourceRepository.delete(source);
    }

    private List<Source> loadSources(TrackRoot trackRoot) {
        if (trackRoot == null || trackRoot.getPath() == null || trackRoot.getPath().isBlank()) {
            logger.warn("TrackRoot or its path is null/blank: " + trackRoot);
            return List.of();
        }

        Path rootPath = Path.of(trackRoot.getPath());
        if (!Files.exists(rootPath)) {
            logger.warn("TrackRoot path does not exist: " + rootPath);
            return List.of();
        }

        List<Source.SourceType> configuredTypes = trackRoot.getAllowedSourceTypes();
        if (configuredTypes == null || configuredTypes.isEmpty()) {
            logger.warn("TrackRoot has no allowed source types defined, defaulting to all types: " + trackRoot);
            configuredTypes = List.of(Source.SourceType.values());
        }
        final List<Source.SourceType> allowedTypes = configuredTypes;

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
                throw new UncheckedIOException("Failed to walk track root: " + trackRoot.getPath(), e);
            }
            return sources;
        }

        logger.warn("TrackRoot path is not a file or directory: " + rootPath);
        return List.of();
    }

    private Source createSource(Path path, List<Source.SourceType> allowedTypes) {
        Source.SourceType sType = resolveSourceType(path);
        if (sType == null) {
            logger.warn("Unrecognized file type for source, Dropped: " + path);
            return null;
        }

        if (!allowedTypes.contains(sType)) {
            logger.info("Source type not allowed by track root config, Dropped: " + path);
            return null;
        }

        // set dummy hash to trigger sync for new sources
        return new Source(path.toAbsolutePath().normalize().toString(), sType);
    }

    private Source.SourceType resolveSourceType(Path path) {
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
        if (fileName.endsWith(".json")) {
            return Source.SourceType.JSON;
        }

        return null;
    }

    private String computeContentHash(Path path) {
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
            throw new UncheckedIOException("Failed to read file for hashing: " + path, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
