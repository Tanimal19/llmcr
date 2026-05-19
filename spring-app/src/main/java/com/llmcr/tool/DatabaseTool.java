package com.llmcr.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.llmcr.util.StringUtils;

@Component
public class DatabaseTool {

    private static final int MAX_RESULT_ROWS = 20;

    private final ContextRepository contextRepository;
    private final QueryContextRetriever queryContextRetriever;

    public DatabaseTool(ContextRepository contextRepository, QueryContextRetriever queryContextRetriever) {
        this.contextRepository = contextRepository;
        this.queryContextRetriever = queryContextRetriever;
    }

    @Tool(description = """
                Search the knowledge base for documents relevant to a query. Use this tool when you need to identify potentially relevant documents before reading their full contents. Use concise semantic queries. Prefer domain keywords over natural language questions. Returns only document ids and names.
            """)
    public String similaritySearch(@ToolParam(required = true) String query) {
        if (query == null || query.isBlank()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "query must not be blank");
            return StringUtils.jsonString(errorResponse);
        }
        ContextRetrievalConfiguration retrievalConfiguration = new ContextRetrievalConfiguration(
                MAX_RESULT_ROWS,
                new FixedKStrategy(),
                "all",
                false);
        ContextRetrievalRequest request = new ContextRetrievalRequest(List.of(query.trim()), retrievalConfiguration);
        List<ContextScorePair> retrievedContexts = queryContextRetriever.retrieve(request);

        if (retrievedContexts.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("results", List.of());
            emptyResponse.put("message", "Query returned no results");
            return StringUtils.jsonString(emptyResponse);
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
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("results", List.of());
            emptyResponse.put("message", "Query returned empty results");
            return StringUtils.jsonString(emptyResponse);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Context context : orderedContexts) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", context.getId());
            item.put("name", context.getName());
            results.add(item);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("count", results.size());
        return StringUtils.jsonString(response);
    }

    @Tool(description = """
                Retrieve the full content of a document by its id. Use this tool after you have identified relevant document ids using similaritySearch() and want to inspect their contents.
            """)
    public String getDocumentById(
            @ToolParam(description = "The exact id of the document to retrieve.", required = true) Long id) {
        return contextRepository.findById(id)
                .map(c -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", c.getId());
                    response.put("name", c.getName());
                    response.put("content", c.getContent() == null ? "" : c.getContent());
                    return StringUtils.jsonString(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "No context found with id: " + id);
                    return StringUtils.jsonString(errorResponse);
                });
    }
}