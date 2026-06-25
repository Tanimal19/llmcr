package com.llmcr.feature.sync.etl.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.entity.ProjectIssue;
import com.llmcr.domain.entity.Source;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

/** Extracts a single issue (e.g. proj-issues-dir/2.json) into a {@link ProjectIssue} context. */
@Component
public class IssueExtractor implements SourceExtractor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(Source source) {
        if (source.getType() != Source.SourceType.JSON) {
            return false;
        }
        JsonNode root = readRootNode(source);
        return root != null && root.has("id") && root.has("title") && root.has("closed");
    }

    @Override
    public List<Context> apply(Source source) {
        JsonNode root = readRootNode(source);
        if (root == null) {
            return List.of();
        }

        int issueNumber = root.path("id").asInt();
        String title = root.path("title").asText("");
        String description = root.path("description").asText("");
        boolean closed = root.path("closed").asBoolean(false);
        LocalDate date = parseDate(root.path("date").asText(null));

        String content = "Title: "
                + title
                + "\n\nDescription: "
                + description
                + "\n\n"
                + formatComments(root.path("comments"));

        return List.of(new ProjectIssue(
                source, 0, "Issue::" + issueNumber, content, issueNumber, title, closed, date));
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
