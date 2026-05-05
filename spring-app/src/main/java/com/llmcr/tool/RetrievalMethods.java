package com.llmcr.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.repository.ContextRepository;

@Component
public class RetrievalMethods {

    private static final Logger log = LoggerFactory.getLogger(RetrievalMethods.class);

    private static final int MAX_RESULT_ROWS = 50;
    private static final int MAX_CELL_CHARS = 500;

    private static final Set<String> ALLOWED_TYPES = Set.of("CLASSNODE", "DOCUMENT", "USECASE", "TOOLDEF");

    private static final Lock CLI_LOCK = new ReentrantLock();

    private final ContextRepository contextRepository;
    private final BufferedReader stdinReader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

    public RetrievalMethods(ContextRepository contextRepository) {
        this.contextRepository = contextRepository;
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

    @Tool(description = """
            Retrieve Context records by filtering on context type and/or keyword strings. \
            Allowed type values: CLASSNODE, DOCUMENT, USECASE, TOOLDEF. \
            nameKeyword and contentKeyword perform case-insensitive substring matches on the 'name' \
            and 'content' fields respectively. All three filters are optional but at least one must \
            be provided. Results are capped at 50 rows; content longer than 500 characters is truncated.""")
    public String retrieveContext(
            @ToolParam(description = "Optional context type filter. One of: CLASSNODE, DOCUMENT, USECASE, TOOLDEF.", required = false) String contextType,
            @ToolParam(description = "Optional keyword for case-insensitive substring match on the 'name' field.", required = false) String nameKeyword,
            @ToolParam(description = "Optional keyword for case-insensitive substring match on the 'content' field.", required = false) String contentKeyword) {

        if ((contextType == null || contextType.isBlank())
                && (nameKeyword == null || nameKeyword.isBlank())
                && (contentKeyword == null || contentKeyword.isBlank())) {
            return "(tool error: at least one of contextType, nameKeyword, or contentKeyword must be provided)";
        }

        ContextType resolvedType = null;
        if (contextType != null && !contextType.isBlank()) {
            String upper = contextType.trim().toUpperCase();
            if (!ALLOWED_TYPES.contains(upper)) {
                return "(tool error: invalid contextType '" + contextType + "'. Allowed values: " + ALLOWED_TYPES + ")";
            }
            resolvedType = ContextType.valueOf(upper);
        }

        String resolvedName = (nameKeyword == null || nameKeyword.isBlank()) ? null : nameKeyword.trim();
        String resolvedContent = (contentKeyword == null || contentKeyword.isBlank()) ? null : contentKeyword.trim();

        List<Context> results = contextRepository.findByFilter(
                resolvedType, resolvedName, resolvedContent,
                PageRequest.of(0, MAX_RESULT_ROWS));

        if (results.isEmpty()) {
            return "Query returned no results.";
        }

        StringBuilder output = new StringBuilder();
        output.append("Query returned ").append(results.size()).append(" row(s).\n");

        for (int i = 0; i < results.size(); i++) {
            Context c = results.get(i);
            String content = c.getContent() == null ? "NULL" : c.getContent();
            if (content.length() > MAX_CELL_CHARS) {
                content = content.substring(0, MAX_CELL_CHARS) + "...(truncated)";
            }
            output.append("\n### Row ").append(i + 1).append("\n")
                    .append("- id: ").append(c.getId()).append("\n")
                    .append("- name: ").append(c.getName()).append("\n")
                    .append("- type: ").append(c.getType()).append("\n")
                    .append("- content:\n").append(content).append("\n");
        }

        return output.toString();
    }
}