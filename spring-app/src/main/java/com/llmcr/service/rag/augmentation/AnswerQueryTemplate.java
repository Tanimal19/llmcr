package com.llmcr.service.rag.augmentation;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;

public class AnswerQueryTemplate extends RAGTemplate {
    @Override
    public List<String> getQueries(Object input) {
        if (input instanceof String query) {
            return List.of(query);
        } else {
            throw new IllegalArgumentException("Input must be of type String");
        }
    }

    @Override
    public RAGTemplate.PromptBuilder getBuilder() {
        return new PromptBuilder();
    }

    public class PromptBuilder extends RAGTemplate.PromptBuilder {
        private final String template = """
                You are a expert software engineer. Your task is to answer a given query based on provided context.

                <query>

                Context information at below.
                -----------------
                <context>
                -----------------

                Rules:
                1. If you can't answer from the given information, just say so, don't make assumptions.
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

        @Override
        protected void doAugmentInput(Object input) {
            if (input instanceof String query) {
                this.variables.put("query", query);
            } else {
                throw new IllegalArgumentException("Input must be of type String");
            }
        }

        @Override
        protected void doAugmentContext(List<Document> documents) {
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                contextBuilder.append((i + 1)).append(". ").append(documents.get(i).getText()).append("\n");
            }
            this.variables.put("context", contextBuilder.toString());
        }
    }
}
