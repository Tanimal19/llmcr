package com.llmcr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * This class represents the java mapping of the system configuration defined in
 * the YAML file.
 */
public record SystemConfig(
        DatasetsConfig datasets,
        ModelsConfig models,
        AgentsConfig agents,
        ExportsConfig exports) {

    public record DatasetsConfig(
            @JsonProperty("proj-code-dir") String projCodeDir,
            @JsonProperty("proj-docs-dir") String projDocsDir,
            @JsonProperty("proj-issues-dir") String projIssuesDir,
            @JsonProperty("proj-prs-dir") String projPrsDir,
            @JsonProperty("review-guideline-json") String reviewGuidelineJson,
            @JsonProperty("docs-dir") String docsDir) {
    }

    public record ModelsConfig(
            @JsonProperty("chat-models") List<ModelConfig> chatModels,
            @JsonProperty("embedding-model") ModelConfig embeddingModel,
            @JsonProperty("reranking-model") ModelConfig rerankingModel) {
    }

    public record ModelConfig(String name, String provider) {
    }

    public record AgentsConfig(
            @JsonProperty("default-chat-model") String defaultChatModel,
            @JsonProperty("enricher-slm") String enricherSlm,
            @JsonProperty("interpretation-llm") String interpretationLlm,
            @JsonProperty("drafting-llm") String draftingLlm,
            @JsonProperty("computation-slm") String computationSlm,
            @JsonProperty("retrieval-slm") String retrievalSlm,
            @JsonProperty("summary-llm") String summaryLlm) {
    }

    public record ExportsConfig(@JsonProperty("review-output-dir") String reviewOutputDir) {
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "datasets", datasets,
                "models", models,
                "agents", agents,
                "exports", exports);
    }
}
