package com.llmcr.service.review.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.retrieval.RetrievalAgent;
import com.llmcr.service.review.agent.retrieval.RetrievalAgent.RetrievalAgentInput;
import com.llmcr.service.review.agent.retrieval.RetrievalAgent.RetrievalAgentOutput;
import com.llmcr.service.review.agent.retrieval.RetrievalAgent.ToolRequest;
import com.llmcr.tool.RetrievalMethods;

/**
 * Loops the RetrievalAgent until it signals satisfied or MAX_ITERATIONS is
 * reached.
 * On each iteration the agent decides which tools to call; this class
 * dispatches
 * those calls against {@link RetrievalMethods} and feeds the responses back.
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
    private final RetrievalMethods retrievalMethods;

    public RetrievalLoop(RetrievalAgent retrievalAgent, RetrievalMethods retrievalMethods) {
        this.retrievalAgent = retrievalAgent;
        this.retrievalMethods = retrievalMethods;
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
        Map<String, Object> args = req.arguments() != null ? req.arguments() : Map.of();
        return switch (req.toolName()) {
            case "askUserQuestion" -> retrievalMethods.askUserQuestion(
                    stringArg(args, "question"));
            case "retrieveContext" -> retrievalMethods.retrieveContext(
                    stringArg(args, "contextType"),
                    stringArg(args, "nameKeyword"),
                    stringArg(args, "contentKeyword"));
            default -> {
                log.warn("[RetrievalLoop] unknown tool: {}", req.toolName());
                yield "(tool error: unknown tool '" + req.toolName() + "')";
            }
        };
    }

    private String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val == null ? null : val.toString();
    }
}
