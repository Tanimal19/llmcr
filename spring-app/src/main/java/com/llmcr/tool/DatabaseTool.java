package com.llmcr.tool;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.rag.QueryContextRetriever;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.FixedKStrategy;

@Component
public class DatabaseTool {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTool.class);

    private static final int MAX_RESULT_ROWS = 20;

    private final ContextRepository contextRepository;
    private final QueryContextRetriever queryContextRetriever;

    public DatabaseTool(ContextRepository contextRepository, QueryContextRetriever queryContextRetriever) {
        this.contextRepository = contextRepository;
        this.queryContextRetriever = queryContextRetriever;
    }

    @Tool(description = """
            Get documents relevant to a query. Use this when you don't know what documents relevant to the user query. Return only the id and name of the documents, you should call getDocumentById with the id to get the full content of the document.""")
    public String similaritySearch(@ToolParam(required = true) String query) {
        if (query == null || query.isBlank()) {
            return "(tool error: query must not be blank)";
        }

        ContextRetrievalConfiguration retrievalConfiguration = new ContextRetrievalConfiguration(
                MAX_RESULT_ROWS,
                new FixedKStrategy(),
                "all",
                false);
        ContextRetrievalRequest request = new ContextRetrievalRequest(List.of(query.trim()), retrievalConfiguration);
        List<ContextScorePair> retrievedContexts = queryContextRetriever.retrieve(request);

        if (retrievedContexts.isEmpty()) {
            return "Query returned no results.";
        }

        List<Context> orderedContexts = new ArrayList<>();
        for (ContextScorePair contextScore : retrievedContexts) {
            Context context = contextScore.context();
            if (context == null) {
                continue;
            }

            boolean alreadyAdded = orderedContexts.stream()
                    .anyMatch(existing -> existing.getId().equals(context.getId()));
            if (!alreadyAdded) {
                orderedContexts.add(context);
            }
        }

        if (orderedContexts.isEmpty()) {
            return "Query returned empty results.";
        }

        StringBuilder output = new StringBuilder();
        output.append("Query returned\n");

        for (Context context : orderedContexts) {
            output.append("\n- id: ").append(context.getId()).append("\n")
                    .append("  ").append(context.getName()).append("\n");
        }

        return output.toString();
    }

    @Tool(description = "Return the full content of a document with the given id. Use this tool when you already have the id of the document and you think the content of the document is relevant to the user query.")
    public String getDocumentById(
            @ToolParam(description = "The exact id of the context to retrieve.", required = true) Long id) {
        logger.info("[ToolCall] tool=getFullContentById id={}", id);

        return contextRepository.findById(id)
                .map(c -> {
                    String content = c.getContent() == null ? "NULL" : c.getContent();
                    return "id: " + c.getId() + "\n"
                            + "name: " + c.getName() + "\n"
                            + "content:\n" + content;
                })
                .orElse("No context found with id: " + id);
    }

}