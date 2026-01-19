package com.example.llmcr.service.rag.augmentation;

import java.util.List;
import java.util.Map;

public class CodeReviewTemplate extends BasePullRequestTemplate {

    @Override
    public List<String> doGetQueries(PullRequest pr) {
        List<String> queries = new java.util.ArrayList<>();
        queries.add(pr.description());
        queries.addAll(pr.hunks().stream().map(hunk -> hunk.toString()).toList());
        return queries;
    }

    @Override
    public RAGTemplate.PromptBuilder getBuilder() {
        return new PromptBuilder();
    }

    public class PromptBuilder extends BasePullRequestTemplate.PromptBuilder {
        private final String template = """
                You are a code reviewer. Given the pull request description, code change hunks, and relevant project context, your task is to generate a concise review on the quality of the code change.

                Pull request description at below.
                -----------------
                <description>
                -----------------

                Code change hunks at below.
                -----------------
                <hunks>
                -----------------

                Relevant context at below.
                -----------------
                <context>
                -----------------

                Rules:
                1. Focus on the changes made in the code, not on unchanged parts.
                2. If you can't determine the quality of the change from the given information, just say so, don't make assumptions.
                3. Do not use statements like "Based on the code change, it seems that...".
                4. You should end with a clear recommendation: "Approve", "Request changes", or "Reject".
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