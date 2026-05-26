package com.llmcr.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class PullRequestParser {

    public record CommentEntry(
            String type,
            String poster,
            @JsonProperty("created_at") String createdAt,
            String body,
            String state) {
    }

    public record ChangedFileEntry(
            String path,
            @JsonProperty("previous_path") String previousPath,
            String patch,
            String content) {
    }

    public record PullRequestData(
            @JsonProperty("pr_id") int prId,
            String url,
            String title,
            @JsonProperty("pr_description") String prDescription,
            @JsonProperty("is_closed") boolean isClosed,
            @JsonProperty("is_merged") boolean isMerged,
            @JsonProperty("is_approved") boolean isApproved,
            List<CommentEntry> comments,
            @JsonProperty("changed_files") List<ChangedFileEntry> changedFiles) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PullRequestParser() {
    }

    /**
     * Parse a JSON file into a {@link PullRequestData} instance.
     */
    public static PullRequestData parseJsonFile(String jsonFilePath) {
        if (jsonFilePath == null || jsonFilePath.isBlank()) {
            throw new IllegalArgumentException("jsonFilePath cannot be null or blank");
        }

        Path path = Paths.get(jsonFilePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("JSON file does not exist: " + jsonFilePath);
        }

        try {
            return MAPPER.readValue(Files.readString(path), PullRequestData.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSON file: " + jsonFilePath, e);
        }
    }
}
