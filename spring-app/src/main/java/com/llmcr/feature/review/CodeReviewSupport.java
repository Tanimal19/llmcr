package com.llmcr.feature.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.feature.review.CodeReviewReport.CodeChange;
import com.llmcr.feature.review.PullRequestParser.PullRequestData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class CodeReviewSupport {

  private static final ObjectMapper REPORT_OBJECT_MAPPER = new ObjectMapper();
  private static final String DEFAULT_REPORT_FILE_NAME = "review.json";
  private static final String DEFAULT_CACHE_SUBDIR = "cache-java";

  private static final int MAX_CHANGED_FILES = 20;
  private static final int MAX_TOTAL_DIFF_SIZE = 100_000;

  public static PullRequestData parsePullRequestData(
      String inputFilePath, Integer jsonlIndex, boolean useMockData) {
    try {
      if (useMockData) {
        return PullRequestParser.parseJsonFile(MockReviewData.MOCK_PULL_REQUEST_JSON_PATH);
      }
      if (jsonlIndex != null) {
        return PullRequestParser.parseJsonlFile(inputFilePath, jsonlIndex);
      }
      if (isJsonlPath(inputFilePath)) {
        return PullRequestParser.parseJsonlFile(inputFilePath, 0);
      }
      return PullRequestParser.parseJsonFile(inputFilePath);
    } catch (Exception ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PARSE_FAILED,
          "Failed to parse pull request data: inputFilePath="
              + inputFilePath
              + ", jsonlIndex="
              + jsonlIndex,
          ex);
    }
  }

  public static List<CodeChange> toCodeChanges(PullRequestData prData) {
    return prData.changedFiles().stream()
        .map(file -> new CodeChange(file.path(), file.patch()))
        .toList();
  }

  public static void validatePullRequestSize(List<CodeChange> codeChanges) {
    int changedSize = codeChanges.stream().mapToInt(change -> change.diffContent().length()).sum();
    if (codeChanges.size() > MAX_CHANGED_FILES || changedSize > MAX_TOTAL_DIFF_SIZE) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PR_TOO_LARGE,
          "Pull request has too many changed files: "
              + codeChanges.size()
              + " files, total diff size: "
              + changedSize);
    }
  }

  /**
   * Rebuilds the changed Java files from the pull request data into a cache directory for analysis.
   *
   * @return the path to the cache directory containing the changed Java files
   */
  public static Path rebuildChangedJavaFilesToCache(
      PullRequestData prData, String outputDir, String prefixDirectory) {
    Path cacheDirectory =
        Paths.get(outputDir, prefixDirectory, DEFAULT_CACHE_SUBDIR).toAbsolutePath().normalize();
    try {
      Files.createDirectories(cacheDirectory);
      for (PullRequestParser.ChangedFileEntry changedFile : prData.changedFiles()) {
        if (!isJavaPath(changedFile.path())
            || changedFile.content() == null
            || changedFile.content().isBlank()) {
          continue;
        }

        Path safeRelativePath = sanitizeRelativePath(changedFile.path());
        Path targetPath = cacheDirectory.resolve(safeRelativePath).normalize();
        if (!targetPath.startsWith(cacheDirectory)) {
          throw new APIServiceException(
              APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
              "Invalid changed file path outside cache directory: " + changedFile.path());
        }

        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, changedFile.content());
      }
      return cacheDirectory;
    } catch (IOException ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
          "Failed to rebuild changed Java files into cache directory",
          ex);
    }
  }

  /** Recursively deletes the cache directory and all its contents */
  public static void cleanupCacheDirectory(Path cacheDirectory) {
    if (cacheDirectory == null || !Files.exists(cacheDirectory)) {
      return;
    }

    try (Stream<Path> stream = Files.walk(cacheDirectory)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ex) {
                  throw new APIServiceException(
                      APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
                      "Failed to delete cache path: " + path,
                      ex);
                }
              });
    } catch (IOException ex) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
          "Failed to walk cache directory for cleanup: " + cacheDirectory,
          ex);
    }
  }

  public static Path writeReport(
      CodeReviewReport report, String outputDir, String prefixDirectory) {
    try {
      Path dir = Paths.get(outputDir, prefixDirectory);
      Files.createDirectories(dir);
      Path reportPath = dir.resolve(DEFAULT_REPORT_FILE_NAME);
      Files.writeString(reportPath, REPORT_OBJECT_MAPPER.writeValueAsString(report));
      return reportPath;
    } catch (IOException e) {
      throw new APIServiceException(APIServiceException.ErrorCode.REVIEW_REPORT_WRITE_FAILED, e);
    }
  }

  private static boolean isJsonlPath(String inputFilePath) {
    return inputFilePath != null && inputFilePath.toLowerCase().endsWith(".jsonl");
  }

  private static boolean isJavaPath(String path) {
    return path != null && path.toLowerCase().endsWith(".java");
  }

  /**
   * Sanitizes the changed file path to ensure it is a relative path that does not traverse outside
   * the intended cache directory. It normalizes path separators and checks for path traversal
   * patterns.
   */
  private static Path sanitizeRelativePath(String changedFilePath) {
    if (changedFilePath == null || changedFilePath.isBlank()) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
          "Changed file path cannot be blank");
    }

    String normalizedSeparators = changedFilePath.replace('\\', '/');
    while (normalizedSeparators.startsWith("/")) {
      normalizedSeparators = normalizedSeparators.substring(1);
    }

    Path relative = Paths.get(normalizedSeparators).normalize();
    if (relative.isAbsolute() || relative.startsWith("..")) {
      throw new APIServiceException(
          APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED,
          "Changed file path must be relative and inside repository: " + changedFilePath);
    }

    return relative;
  }
}
