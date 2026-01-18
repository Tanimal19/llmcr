package com.example.llmcr.service.rag.augmentation;

import java.util.Map;

public class CodeInterpretationPromptBuilder extends BasePullRequestPromptBuilder {
    private final String template = """
            You are a code interpreter. Given the code change hunks and relevant project context, your task is to explain what does the code change do and why does the code change is proposed.

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
            2. If you can't determine what the code does from the given information, just say so, don't make assumptions.
            3. Do not use statements like "Based on the code change, it seems that...".
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

    @Override
    public String getQuery() {
        assert isInputAugmented : "Input must be augmented before getting the query.";
        return (String) this.getVariables().get("hunks");
    }

}
