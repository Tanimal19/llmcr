package com.example.llmcr.service.rag.augmentation;

import java.util.List;
import java.util.Map;

public class CodeInterpretationTemplate extends BasePullRequestTemplate {
    @Override
    public List<String> doGetQueries(PullRequest pr) {
        // build each hunk as a separate query
        List<String> queries = new java.util.ArrayList<>();
        queries.addAll(pr.hunks().stream().map(
                hunk -> "Task: write a CL description including 'what change is being made?' and 'why are these changes being made?'. Given code changes: "
                        + hunk.toString())
                .toList());
        return queries;
    }

    @Override
    public RAGTemplate.PromptBuilder getBuilder() {
        return new PromptBuilder();
    }

    public class PromptBuilder extends BasePullRequestTemplate.PromptBuilder {
        private final String template = """
                You are a experienced Java developer. Given the code change hunks and relevant project context, your task is to write a code change description. A code change description is a public record of change, and it is important that it communicates:
                1. What change is being made? This should summarize the major changes such that readers have a sense of what is being changed without needing to read the entire CL. The description should be concise and to the point.
                2. Why are these changes being made? What contexts did you have as an author when making this change? Were there decisions you made that aren't reflected in the source code? etc.

                Code change hunks at below.
                -----------------
                <hunks>
                -----------------

                Relevant context at below.
                -----------------
                <context>
                -----------------

                Rules:
                1. Don't make assumptions on anything that is not clear from the given information.
                2. Do not use statements like "Based on the code change, it seems that...".
                """;

        private final Map<String, Object> variables = new java.util.HashMap<>();

        @Override
        protected String getTemplate() {
            return template;
        }

        @Override
        protected Map<String, Object> getVariables() {
            return variables;
        }
    }
}
