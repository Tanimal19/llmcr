package com.llmcr.feature.sync.source;

import com.llmcr.domain.entity.Source;
import com.llmcr.domain.entity.Source.SourceType;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.exception.APIServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalSourceScanner {

  private static final Logger logger = LoggerFactory.getLogger(LocalSourceScanner.class);

  public List<Source> getLocalSources(TrackRoot trackRoot) {
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

  private List<Source> scanLocalSources(
      TrackRoot trackRoot, Path rootPath, Set<SourceType> allowedTypes) {
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

  public Source createSource(Path path, Set<SourceType> allowedTypes) {
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

  public SourceType resolveSourceType(Path path) {
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

  public static String computeContentHash(Path path) {
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
}
