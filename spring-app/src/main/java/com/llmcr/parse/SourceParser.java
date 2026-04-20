package com.llmcr.parse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.llmcr.entity.Source;
import com.llmcr.entity.TrackRoot;

/**
 * Parse sources within a TrackRoot.
 */
public class SourceParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceParser.class);

    public List<Source> getAllSourceInRoot(TrackRoot trackRoot) {
        if (trackRoot == null || trackRoot.getPath() == null || trackRoot.getPath().isBlank()) {
            LOGGER.warn("TrackRoot or its path is null/blank: " + trackRoot);
            return List.of();
        }

        Path rootPath = Path.of(trackRoot.getPath());
        if (!Files.exists(rootPath)) {
            LOGGER.warn("TrackRoot path does not exist: " + rootPath);
            return List.of();
        }

        List<Source> sources = new ArrayList<>();
        if (Files.isRegularFile(rootPath)) {
            sources.add(buildSource(rootPath, trackRoot));
            return sources;
        }

        if (!Files.isDirectory(rootPath)) {
            LOGGER.warn("TrackRoot path is not a file or directory: " + rootPath);
            return List.of();
        }

        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> buildSource(path, trackRoot))
                    .forEach(sources::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk track root: " + trackRoot.getPath(), e);
        }

        return sources;
    }

    private Source buildSource(Path path, TrackRoot trackRoot) {
        Source.SourceType sType = resolveSourceType(path);
        if (sType == null) {
            LOGGER.warn("Unrecognized file type for source, Dropped: " + path);
            return null;
        }

        Source source = new Source();
        source.setSourcePath(path.toAbsolutePath().normalize().toString());
        source.setSourceType(sType);
        source.setContentHash(computeContentHash(path));
        source.setParentTrackRoot(trackRoot);
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

        return null;
    }

    private String computeContentHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);

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
