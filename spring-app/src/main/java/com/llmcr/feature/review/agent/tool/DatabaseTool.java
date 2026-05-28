package com.llmcr.feature.review.agent.tool;

import com.llmcr.domain.repository.ContextRepository;
import com.llmcr.domain.util.StringUtils;
import com.llmcr.infrastructure.rag.ContextScorePair;
import com.llmcr.infrastructure.rag.QueryContextRetriever;
import com.llmcr.infrastructure.rag.QueryContextRetriever.QueryContextRetrievalConfig;
import com.llmcr.infrastructure.rag.QueryContextRetriever.QueryContextRetrievalRequest;
import com.llmcr.infrastructure.rag.fusion.RankFusionStrategy;
import com.llmcr.infrastructure.rag.select.FixedKStrategy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DatabaseTool {

  private static final int MAX_RESULT_ROWS = 20;
  private static final String COLLECTION_NAME = "all";

  private final ContextRepository contextRepository;
  private final QueryContextRetriever queryContextRetriever;

  public DatabaseTool(
      ContextRepository contextRepository, QueryContextRetriever queryContextRetriever) {
    this.contextRepository = contextRepository;
    this.queryContextRetriever = queryContextRetriever;
  }

  @Tool(description = "Find documents by semantic query and return matching document ids.")
  public String findDocuments(
      @ToolParam(
              description =
                  "A concise semantic queries. Prefer domain keywords over natural language questions.",
              required = true)
          String query) {
    if (query == null || query.isBlank()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "query must not be blank");
      return StringUtils.jsonString(errorResponse);
    }
    QueryContextRetrievalConfig retrievalConfiguration =
        new QueryContextRetrievalConfig(
            COLLECTION_NAME,
            MAX_RESULT_ROWS,
            new FixedKStrategy(),
            new RankFusionStrategy(),
            false);
    QueryContextRetrievalRequest request =
        new QueryContextRetrievalRequest(List.of(query.trim()), retrievalConfiguration);
    List<ContextScorePair> retrievedContexts = queryContextRetriever.retrieve(request);

    if (retrievedContexts.isEmpty()) {
      Map<String, Object> emptyResponse = new HashMap<>();
      emptyResponse.put("results", List.of());
      emptyResponse.put("message", "Query returned no results");
      return StringUtils.jsonString(emptyResponse);
    }

    List<Map<String, Object>> results = new ArrayList<>();
    for (ContextScorePair context : retrievedContexts) {
      Map<String, Object> item = new HashMap<>();
      item.put("id", context.context().getId());
      item.put("name", context.context().getName().split("::")[1]);
      item.put("relevanceScore", context.score());
      results.add(item);
    }

    Map<String, Object> response = new HashMap<>();
    response.put("results", results);
    response.put("count", results.size());
    return StringUtils.jsonString(response);
  }

  @Tool(
      description =
          "Fetch full document content using an exact integer document id. NEVER use this tool when you are not sure about the exact document id.")
  public String fetchDocumentcontent(
      @ToolParam(description = "The exact integer id of the document to retrieve.", required = true)
          Long id) {
    return contextRepository
        .findById(id)
        .map(
            c -> {
              Map<String, Object> response = new HashMap<>();
              response.put("id", c.getId());
              response.put("name", c.getName());
              response.put("content", c.getContent() == null ? "" : c.getContent());
              return StringUtils.jsonString(response);
            })
        .orElseGet(
            () -> {
              Map<String, Object> errorResponse = new HashMap<>();
              errorResponse.put("error", "No context found with id: " + id);
              return StringUtils.jsonString(errorResponse);
            });
  }
}
