package com.llmcr.tool;

import java.util.List;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.repository.ContextRepository;

@Component
public class DatabaseTool {

    private static final int MAX_RESULT_ROWS = 20;
    private static final int MAX_CELL_CHARS = 500;

    private static final Set<String> ALLOWED_TYPES = Set.of("CLASSNODE", "DOCUMENT", "USECASE", "TOOLDEF");

    private final ContextRepository contextRepository;

    public DatabaseTool(ContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    @Tool(description = """
            Retrieve Context records by filtering on context type and/or keyword strings. \
            Allowed type values: CLASSNODE, DOCUMENT, USECASE, TOOLDEF. \
            nameKeyword and contentKeyword perform case-insensitive substring matches on the 'name' \
            and 'content' fields respectively. All three filters are optional but at least one must \
            be provided.""")
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
                    .append("- name: ").append(c.getName()).append("\n")
                    .append("- type: ").append(c.getType()).append("\n")
                    .append("- content:\n").append(content).append("\n");
        }

        return output.toString();
    }
}