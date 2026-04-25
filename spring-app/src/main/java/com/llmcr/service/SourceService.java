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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Source;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.repository.SourceRepository;

/**
 * Service for managing data sources (files)
 */
@Service
public class SourceService {

    private static final Logger logger = LoggerFactory.getLogger(SourceService.class);

    private final TrackRootRepository trackRootRepository;
    private final SourceRepository sourceRepository;

    public SourceService(TrackRootRepository trackRootRepository, SourceRepository sourceRepository) {
        this.trackRootRepository = trackRootRepository;
        this.sourceRepository = sourceRepository;
    }

    /**
     * 1. Reconcile local files with database records.
     * - Remove database sources that no longer exist locally.
     * - Add new local sources that are not in database.
     * 2. Recompute content hash and update last sync time.
     */
    @Transactional
    public void refreshTrackRoots() {
        trackRootRepository.findAll().forEach(this::reconcileSource);
        syncAllSources();
    }

    private void reconcileSource(TrackRoot trackRoot) {
        Map<String, Source> localSourcesByPath = new LinkedHashMap<>();

        List<Source> localSources = loadSources(trackRoot);
        for (Source localSource : localSources) {
            localSource.setTrackRoot(trackRoot);
            Source duplicated = localSourcesByPath.putIfAbsent(localSource.getPath(), localSource);
            if (duplicated != null) {
                logger.warn("Duplicated source path found under track root, keeping first one: "
                        + localSource.getPath());
            }
        }

        List<Source> dbSources = sourceRepository.findAll().stream()
                .filter(source -> Objects.equals(source.getTrackRoot(), trackRoot))
                .toList();

        Map<String, Source> dbSourcesByPath = new LinkedHashMap<>();
        for (Source dbSource : dbSources) {
            dbSourcesByPath.put(dbSource.getPath(), dbSource);
        }

        for (Source dbSource : dbSources) {
            if (!localSourcesByPath.containsKey(dbSource.getPath())) {
                sourceRepository.delete(dbSource);
            }
        }

        for (Map.Entry<String, Source> entry : localSourcesByPath.entrySet()) {
            Source existing = dbSourcesByPath.get(entry.getKey());
            Source localSource = entry.getValue();
            if (existing == null) {
                sourceRepository.save(localSource);
                continue;
            }

            boolean updated = false;

            if (!Objects.equals(existing.getTrackRoot(), localSource.getTrackRoot())) {
                existing.setTrackRoot(localSource.getTrackRoot());
                updated = true;
            }

            if (existing.getType() != localSource.getType()) {
                existing.setType(localSource.getType());
                updated = true;
            }

            if (updated) {
                sourceRepository.save(existing);
            }
        }
    }

    private void syncAllSources() {
        LocalDateTime syncTime = LocalDateTime.now();
        sourceRepository.findAll().forEach(source -> {
            Path sourcePath = Path.of(source.getPath());
            if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
                logger.warn("Source path does not exist or is not a regular file, skip sync: " + sourcePath);
                return;
            }

            String currentHash = computeContentHash(sourcePath);
            if (!Objects.equals(source.getContentHash(), currentHash)) {
                source.setContentHash(currentHash);
            }
            source.setLastSyncTime(syncTime);
            sourceRepository.save(source);
        });
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
            Source source = buildSource(rootPath, allowedTypes);
            if (source != null) {
                sources.add(source);
            }
            return sources;
        }

        if (!Files.isDirectory(rootPath)) {
            logger.warn("TrackRoot path is not a file or directory: " + rootPath);
            return List.of();
        }

        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> buildSource(path, allowedTypes))
                    .filter(Objects::nonNull)
                    .forEach(sources::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk track root: " + trackRoot.getPath(), e);
        }

        return sources;
    }

    private Source buildSource(Path path, List<Source.SourceType> allowedTypes) {
        Source.SourceType sType = resolveSourceType(path);
        if (sType == null) {
            logger.warn("Unrecognized file type for source, Dropped: " + path);
            return null;
        }

        if (!allowedTypes.contains(sType)) {
            logger.info("Source type not allowed by track root config, Dropped: " + path);
            return null;
        }

        Source source = new Source();
        source.setPath(path.toAbsolutePath().normalize().toString());
        source.setType(sType);
        source.setContentHash(computeContentHash(path));
        return source;
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
