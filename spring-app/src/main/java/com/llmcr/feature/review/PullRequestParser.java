package com.llmcr.feature.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public final class PullRequestParser {

  public record CommentEntry(
      String type,
      String poster,
      @JsonProperty("created_at") String createdAt,
      String body,
      String state) {}

  public record ChangedFileEntry(
      String path,
      @JsonProperty("previous_path") String previousPath,
      String patch,
      String content) {}

  public record PullRequestData(
      @JsonProperty("pr_id") int prId,
      String url,
      String title,
      @JsonProperty("pr_description") String prDescription,
      @JsonProperty("is_closed") boolean isClosed,
      @JsonProperty("is_merged") boolean isMerged,
      @JsonProperty("is_approved") boolean isApproved,
      List<CommentEntry> comments,
      @JsonProperty("changed_files") List<ChangedFileEntry> changedFiles) {}

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private PullRequestParser() {}

  /** Parse a JSON file into a {@link PullRequestData} instance. */
  public static PullRequestData parseJsonFile(String jsonFilePath) {
    if (jsonFilePath == null || jsonFilePath.isBlank()) {
      throw new IllegalArgumentException("jsonFilePath cannot be null or blank");
    }

    Path path = Paths.get(jsonFilePath);
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("JSON file does not exist: " + jsonFilePath);
    }

    try {
      return parseJsonContent(Files.readString(path));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read JSON file: " + jsonFilePath, e);
    }
  }

  /**
   * Parse the line at {@code index} (0-based) from a JSONL file into a {@link PullRequestData}
   * instance.
   */
  public static PullRequestData parseJsonlFile(String jsonlFilePath, int index) {
    if (jsonlFilePath == null || jsonlFilePath.isBlank()) {
      throw new IllegalArgumentException("jsonlFilePath cannot be null or blank");
    }
    if (index < 0) {
      throw new IllegalArgumentException("index must be greater than or equal to 0");
    }

    Path path = Paths.get(jsonlFilePath);
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("JSONL file does not exist: " + jsonlFilePath);
    }

    String line;
    try (Stream<String> lines = Files.lines(path)) {
      line =
          lines
              .skip(index)
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No record found at index "
                              + index
                              + " in JSONL file: "
                              + jsonlFilePath));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read JSONL file: " + jsonlFilePath, e);
    }

    if (line.isBlank()) {
      throw new IllegalArgumentException(
          "Record at index " + index + " is blank in JSONL file: " + jsonlFilePath);
    }

    return parseJsonContent(line);
  }

  private static PullRequestData parseJsonContent(String content) {
    try {
      return MAPPER.readValue(content, PullRequestData.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse JSON content", e);
    }
  }
}
