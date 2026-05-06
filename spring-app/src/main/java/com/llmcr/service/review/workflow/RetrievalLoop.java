package com.llmcr.service.review.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.RetrievalAgent;
import com.llmcr.service.review.agent.RetrievalAgent.RetrievalAgentInput;
import com.llmcr.service.review.agent.RetrievalAgent.RetrievalAgentOutput;
import com.llmcr.service.review.agent.RetrievalAgent.ToolRequest;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.UserInteractionTool;

/**
 * Loops the RetrievalAgent until it signals satisfied or MAX_ITERATIONS is
 * reached.
 * On each iteration the agent decides which tools to call; this class
 * dispatches
 * those calls against tool components and feeds the responses back.
 */
@Component
public class RetrievalLoop {

    private static final Logger log = LoggerFactory.getLogger(RetrievalLoop.class);
    private static final int MAX_ITERATIONS = 5;

    /**
     * After this many unsatisfied iterations, skip the agent and ask the user
     * directly.
     */
    private static final int ASK_USER_AFTER_ITERATIONS = 3;

    private final RetrievalAgent retrievalAgent;
    private final UserInteractionTool userInteractionTool;
    private final DatabaseTool databaseTool;

    public RetrievalLoop(RetrievalAgent retrievalAgent, UserInteractionTool userInteractionTool,
            DatabaseTool databaseTool) {
        this.retrievalAgent = retrievalAgent;
        this.userInteractionTool = userInteractionTool;
        this.databaseTool = databaseTool;
    }

    /**
     * Run the retrieval loop for the given {@code dataQuery} and return the
     * aggregated tool responses as a single string.
     */
    public String run(String dataQuery) {
        String currentQuery = dataQuery;
        List<String> toolResponses = new ArrayList<>();

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.debug("[RetrievalLoop] iteration={} query={}", i, currentQuery);

            // Too many retrieval attempts — ask the user directly and stop.
            if (i >= ASK_USER_AFTER_ITERATIONS) {
                log.debug("[RetrievalLoop] reached ask-user threshold at iteration={}, falling back to askUserQuestion",
                        i);
                ToolRequest fallback = new ToolRequest(
                        "askUserQuestion",
                        Map.of("question", currentQuery),
                        "fallback: retrieval did not satisfy the query after " + i + " attempts");
                String result = dispatchTool(fallback);
                toolResponses.add("[" + fallback.purpose() + "]\n" + result);
                break;
            }

            RetrievalAgentOutput output = retrievalAgent.execute(
                    new RetrievalAgentInput(currentQuery, List.copyOf(toolResponses)));

            if (output.satisfied() || output.toolRequests() == null || output.toolRequests().isEmpty()) {
                break;
            }

            for (ToolRequest req : output.toolRequests()) {
                String result = dispatchTool(req);
                toolResponses.add("[" + req.purpose() + "]\n" + result);
            }

            if (output.refinedQuery() != null && !output.refinedQuery().isBlank()) {
                currentQuery = output.refinedQuery();
            }
        }

        return String.join("\n----\n", toolResponses);
    }

    private String dispatchTool(ToolRequest req) {
        if (req == null || req.toolName() == null) {
            return "(tool error: null request)";
        }
        Map<String, Object> args = normalizeArgs(req.toolName(), req.arguments());
        return switch (req.toolName()) {
            case "askUserQuestion" -> userInteractionTool.askUserQuestion(
                    stringArg(args, "question"));
            case "retrieveContext" -> databaseTool.retrieveContext(
                    stringArg(args, "contextType"),
                    stringArg(args, "nameKeyword"),
                    stringArg(args, "contentKeyword"));
            default -> {
                log.warn("[RetrievalLoop] unknown tool: {}", req.toolName());
                yield "(tool error: unknown tool '" + req.toolName() + "')";
            }
        };
    }

    private Map<String, Object> normalizeArgs(String toolName, Object rawArguments) {
        if (rawArguments == null) {
            return Map.of();
        }

        if (rawArguments instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    normalized.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return normalized;
        }

        if (rawArguments instanceof String text && !text.isBlank()) {
            if ("askUserQuestion".equals(toolName)) {
                return Map.of("question", text.trim());
            }
            if ("retrieveContext".equals(toolName)) {
                return Map.of("contentKeyword", text.trim());
            }
            return Map.of("raw", text.trim());
        }

        return Map.of("raw", rawArguments.toString());
    }

    private String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val == null ? null : val.toString();
    }
}
