package com.example.llmcr.service.rag.augmentation;

import java.util.List;

import org.springframework.ai.document.Document;

public abstract class BasePullRequestTemplate extends RAGTemplate {

    public record Hunk(String filepath, String content) {
        public String toString() {
            return "{\"filepath: " + filepath + "\", \"content\": \"" + content + "\"}";
        }
    }

    public record PullRequest(String id, String title, String description, List<Hunk> hunks) {
    }

    @Override
    public List<String> getQueries(Object input) {
        if (input instanceof PullRequest pr) {
            return doGetQueries(pr);
        } else {
            throw new IllegalArgumentException("Input must be of type PullRequest");
        }
    }

    protected abstract List<String> doGetQueries(PullRequest pr);

    private String buildHunksVariable(List<Hunk> hunks) {
        StringBuilder hunksBuilder = new StringBuilder();
        int count = 1;
        for (Hunk hunk : hunks) {
            hunksBuilder.append(count).append(". ").append(hunk).append("\n");
            count++;
        }
        return hunksBuilder.toString();
    }

    public abstract class PromptBuilder extends RAGTemplate.PromptBuilder {
        @Override
        protected void doAugmentInput(Object input) {
            if (input instanceof PullRequest pr) {
                this.getVariables().put("description", pr.description);
                this.getVariables().put("hunks", buildHunksVariable(pr.hunks));
            } else {
                throw new IllegalArgumentException("Input must be of type PullRequest");
            }
        }

        @Override
        protected void doAugmentContext(List<Document> documents) {
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                contextBuilder.append((i + 1)).append(". ").append(documents.get(i).getText()).append("\n");
            }
            this.getVariables().put("context", contextBuilder.toString());
        }
    }
}
