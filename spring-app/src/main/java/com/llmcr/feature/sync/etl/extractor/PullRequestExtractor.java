package com.llmcr.feature.sync.etl.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.entity.ProjectPullRequest;
import com.llmcr.domain.entity.Source;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

/** Extracts a single pull request (e.g. proj-prs-dir/1981.json) into a {@link ProjectPullRequest} context. */
@Component
public class PullRequestExtractor implements SourceExtractor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(Source source) {
        if (source.getType() != Source.SourceType.JSON) {
            return false;
        }
        JsonNode root = readRootNode(source);
        return root != null && root.has("id") && root.has("title") && root.has("result");
    }

    @Override
    public List<Context> apply(Source source) {
        JsonNode root = readRootNode(source);
        if (root == null) {
            return List.of();
        }

        int prNumber = root.path("id").asInt();
        String title = root.path("title").asText("");
        String description = root.path("description").asText("");
        String result = root.path("result").asText("");
        LocalDate date = parseDate(root.path("date").asText(null));

        String content = "Title: "
                + title
                + "\n\nDescription: "
                + description
                + "\n\n"
                + formatComments(root.path("comments"));

        return List.of(new ProjectPullRequest(
                source, 0, "PullRequest::" + prNumber, content, prNumber, title, result, date));
    }

    private String formatComments(JsonNode comments) {
        if (!comments.isArray() || comments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Comments:\n");
        for (JsonNode comment : comments) {
            sb.append("- ").append(comment.path("content").asText("")).append("\n");
        }
        return sb.toString();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(dateStr).toLocalDate();
    }

    private JsonNode readRootNode(Source source) {
        try {
            return objectMapper.readTree(new FileSystemResource(source.getPath()).getInputStream());
        } catch (IOException e) {
            return null;
        }
    }
}
