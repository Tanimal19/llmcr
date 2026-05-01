package com.llmcr.service.review.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.llmcr.service.rag.ContextRetriever;
import com.llmcr.service.rag.ContextRetriever.ContextScorePair;
import com.llmcr.service.rag.ContextRetriever.RetrievalConfiguration;
import com.llmcr.service.rag.select.FixedKStrategy;

@Component
public class RetrievalTools {

    private static final Logger log = LoggerFactory.getLogger(RetrievalTools.class);

    private static final String DEFAULT_COLLECTION = "all";
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;
    private static final int MAX_CONTEXT_CHARS = 1500;

    private static final Lock CLI_LOCK = new ReentrantLock();

    private final ContextRetriever contextRetriever;
    private final BufferedReader stdinReader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

    public RetrievalTools(ContextRetriever contextRetriever) {
        this.contextRetriever = contextRetriever;
    }

    @Tool(description = "Ask the user a follow-up question through the CLI and return the exact answer. Use this when the missing information can only come from the user.")
    public String askUserQuestion(
            @ToolParam(description = "The exact question to ask the user. Keep it short and specific.") String question) {
        if (question == null || question.isBlank()) {
            return "(tool error: question must not be empty)";
        }

        CLI_LOCK.lock();
        try {
            System.out.println("\n[RetrievalAgent] Question for user:\n" + question);
            System.out.print("[User answer] > ");
            String answer = stdinReader.readLine();
            if (answer == null || answer.isBlank()) {
                return "(no user answer provided)";
            }
            return answer.trim();
        } catch (IOException e) {
            log.error("[RetrievalTools] Failed to read CLI input", e);
            return "(tool error: failed to read user input: " + e.getMessage() + ")";
        } finally {
            CLI_LOCK.unlock();
        }
    }

    @Tool(description = "Retrieve relevant context from the vector database using semantic search. Use this tool when you need project knowledge or guideline references to satisfy the data query.")
    public String retrieveContextFromVectorStore(
            @ToolParam(description = "Semantic search query for retrieving relevant context.") String query,
            @ToolParam(description = "Optional number of contexts to return, between 1 and 10. Defaults to 5.", required = false) Integer topK) {

        if (query == null || query.isBlank()) {
            return "(tool error: query must not be empty)";
        }

        int resolvedTopK = topK == null ? DEFAULT_TOP_K : Math.max(1, Math.min(MAX_TOP_K, topK));

        RetrievalConfiguration config = new RetrievalConfiguration(
                resolvedTopK,
                DEFAULT_COLLECTION,
                true,
                new FixedKStrategy());

        List<ContextScorePair> retrieved = contextRetriever.retrieve(List.of(query), config);
        if (retrieved.isEmpty()) {
            return "No context found for query in collection '" + DEFAULT_COLLECTION + "'.";
        }

        StringBuilder output = new StringBuilder();
        output.append("Retrieved ").append(retrieved.size())
                .append(" context item(s) from collection '").append(DEFAULT_COLLECTION).append("'.");

        for (int i = 0; i < retrieved.size(); i++) {
            ContextScorePair pair = retrieved.get(i);
            String content = truncateContent(pair.context().getContent());

            output.append("\n\n### Result ").append(i + 1)
                    .append("\n- name: ").append(pair.context().getName())
                    .append("\n- content:\n")
                    .append(content);
        }

        return output.toString();
    }

    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_CONTEXT_CHARS) {
            return content;
        }
        return content.substring(0, MAX_CONTEXT_CHARS) + "\n...(truncated)";
    }
}