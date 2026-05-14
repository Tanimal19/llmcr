package com.llmcr.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.rag.QueryContextRetriever;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.FixedKStrategy;

@Component
public class DatabaseTool {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTool.class);

    private static final int MAX_RESULT_ROWS = 10;
    private static final int MAX_CELL_CHARS = 800;

    private static final Set<String> ALLOWED_COLLECTIONS = Set.of("project-code", "docs");

    private final ContextRepository contextRepository;
    private final QueryContextRetriever queryContextRetriever;

    public DatabaseTool(ContextRepository contextRepository, QueryContextRetriever queryContextRetriever) {
        this.contextRepository = contextRepository;
        this.queryContextRetriever = queryContextRetriever;
    }

    @Tool(description = """
                Get an overview of most relevant code or document content based on a query. Use this when you want to find relevant information but don't have a specific id.

                Available collections for retrieval:
                - project-code: Use this collection for queries related to project source code and APIs.
                - docs: Use this collection for queries related to understanding software best pratices and other information.
            """)
    public String retrieveDocumentContentByQuery(
            @ToolParam(description = "A query to search for content.", required = true) String query,
            @ToolParam(description = "The collection to be search.", required = true) String collectionName) {
        logger.info("[ToolCall] tool=retrieveDocumentContentByQuery collection={} query={}",
                collectionName, query);

        if (query == null || query.isBlank()) {
            return "(tool error: query must not be blank)";
        }

        if (!ALLOWED_COLLECTIONS.contains(collectionName)) {
            return "(tool error: invalid collectionName '" + collectionName + "'. Allowed values: "
                    + ALLOWED_COLLECTIONS + ")";
        }

        ContextRetrievalConfiguration retrievalConfiguration = new ContextRetrievalConfiguration(
                MAX_RESULT_ROWS,
                new FixedKStrategy(),
                collectionName,
                false);
        ContextRetrievalRequest request = new ContextRetrievalRequest(List.of(query.trim()), retrievalConfiguration);
        List<ContextScorePair> retrievedContexts = queryContextRetriever.retrieve(request);

        if (retrievedContexts.isEmpty()) {
            return "Query returned no results.";
        }

        List<Context> orderedDocuments = new ArrayList<>();
        for (ContextScorePair contextScore : retrievedContexts) {
            Context context = contextScore.context();
            if (context == null || context.getType() != ContextType.DOCUMENT) {
                continue;
            }

            boolean alreadyAdded = orderedDocuments.stream()
                    .anyMatch(existing -> existing.getId().equals(context.getId()));
            if (!alreadyAdded) {
                orderedDocuments.add(context);
            }
        }

        if (orderedDocuments.isEmpty()) {
            return "Query returned no document results.";
        }

        StringBuilder output = new StringBuilder();
        output.append("Query returned ").append(orderedDocuments.size()).append(" document(s).\n");

        for (int i = 0; i < orderedDocuments.size(); i++) {
            Context document = orderedDocuments.get(i);
            String content = document.getContent() == null ? "NULL" : document.getContent();
            if (content.length() > MAX_CELL_CHARS) {
                content = content.substring(0, MAX_CELL_CHARS) + "...(truncated)";
            }
            output.append("\n### Document ").append(i + 1).append("\n")
                    .append("- id: ").append(document.getId()).append("\n")
                    .append("- name: ").append(document.getName()).append("\n")
                    .append("- type: ").append(document.getType()).append("\n")
                    .append("- content:\n").append(content).append("\n");
        }

        return output.toString();
    }

    @Tool(description = "Retrieve code or document content by exact id. Use this when you have a specific id from previous retrieval and want to get the full content.")
    public String retrieveContextById(
            @ToolParam(description = "The exact id of the context to retrieve.", required = true) Long id) {
        logger.info("[ToolCall] tool=retrieveContextById id={}", id);

        return contextRepository.findById(id)
                .map(c -> {
                    String content = c.getContent() == null ? "NULL" : c.getContent();
                    return "id: " + c.getId() + "\n"
                            + "name: " + c.getName() + "\n"
                            + "type: " + c.getType() + "\n"
                            + "content:\n" + content;
                })
                .orElse("No context found with id: " + id);
    }

}