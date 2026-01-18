package com.example.llmcr.service.rag.augmentation;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;

public abstract class BasePullRequestPromptBuilder implements PromptBuilder {

    public record Hunk(String filepath, String content) {
        public String toString() {
            return "{\"filepath: " + filepath + "\", \"content\": \"" + content + "\"}";
        }
    }

    public record PullRequest(String id, String title, String description, List<Hunk> hunks) {
    }

    protected abstract String getTemplate();

    protected abstract Map<String, Object> getVariables();

    protected boolean isInputAugmented = false;
    protected boolean isContextAugmented = false;

    @Override
    public PromptBuilder augmentInput(Object input) {
        if (input instanceof PullRequest pr) {
            System.out.println("Augmenting input for PR: " + pr.title + " (" + pr.id + ")");
            this.getVariables().put("description", pr.description);
            this.getVariables().put("hunks", buildHunksVariable(pr.hunks));
            this.isInputAugmented = true;
        } else {
            throw new IllegalArgumentException("Input must be of type PullRequest");
        }
        return this;
    }

    private String buildHunksVariable(List<Hunk> hunks) {
        StringBuilder hunksBuilder = new StringBuilder();
        int count = 1;
        for (Hunk hunk : hunks) {
            hunksBuilder.append(count).append(". ").append(hunk).append("\n");
            count++;
        }
        return hunksBuilder.toString();
    }

    @Override
    public PromptBuilder augmentContext(List<Document> documents) {
        assert isInputAugmented : "Input must be augmented before context augmentation.";

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            contextBuilder.append((i + 1)).append(". ").append(documents.get(i).getText()).append("\n");
        }
        this.getVariables().put("context", contextBuilder.toString());
        this.isContextAugmented = true;
        return this;
    }

    @Override
    public Prompt build() {
        assert isInputAugmented : "Input must be augmented before building the prompt.";
        assert isContextAugmented : "Context must be augmented before building the prompt.";

        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(this.getTemplate())
                .build()
                .create(this.getVariables());
    }
}
