package com.llmcr.service.rag.augmentation;

import java.util.List;
import java.util.Map;

public class CodeReviewTemplate extends BasePullRequestTemplate {

    @Override
    public List<String> doGetQueries(PullRequest pr) {
        // build each hunk as a separate query
        List<String> queries = new java.util.ArrayList<>();
        queries.add(pr.description());
        queries.addAll(pr.hunks().stream()
                .map(hunk -> "Task: write code review comments that evaluate the quality of the change. Given code changes:"
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
                You are a experienced code reviewer. Given the pull request description, code change hunks, and relevant project context, your task is to write code review comments. A code review is an evaluation of a code change on its quality, correctness, and, most importantly, does the change improves the overall maintainability, readability, and understandability of the system.

                Make sure to review every line of code you've been asked to review, look at the context, make sure you're improving code health, and compliment developers on good things that they do. You should give reasoning for any comments you make.

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