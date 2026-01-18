package com.example.llmcr.service.rag.augmentation;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;

public class AnswerQueryPromptBuilder implements PromptBuilder {
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

    protected boolean isInputAugmented = false;
    protected boolean isContextAugmented = false;

    @Override
    public PromptBuilder augmentInput(Object input) {
        if (input instanceof String query) {
            this.variables.put("query", query);
            this.isInputAugmented = true;
        } else {
            throw new IllegalArgumentException("Input must be of type PullRequest");
        }
        return this;
    }

    @Override
    public PromptBuilder augmentContext(List<Document> documents) {
        assert isInputAugmented : "Input must be augmented before context augmentation.";

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            contextBuilder.append((i + 1)).append(". ").append(documents.get(i).getText()).append("\n");
        }
        this.variables.put("context", contextBuilder.toString());
        this.isContextAugmented = true;
        return this;
    }

    @Override
    public Prompt build() {
        assert isInputAugmented : "Input must be augmented before building the prompt.";
        assert isContextAugmented : "Context must be augmented before building the prompt.";

        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(this.template)
                .build()
                .create(this.variables);
    }

    @Override
    public String getQuery() {
        assert isInputAugmented : "Input must be augmented before getting the query.";
        return (String) this.variables.get("query");
    }
}
