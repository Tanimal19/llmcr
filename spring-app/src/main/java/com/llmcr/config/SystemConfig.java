package com.llmcr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.llmcr.domain.entity.Source.SourceType;

import java.util.List;
import java.util.Map;

/**
 * This class represents the java mapping of the system configuration defined in
 * the YAML file.
 */
public record SystemConfig(
        @JsonProperty("track-roots") Map<String, TrackRootConfig> trackRoots,
        Map<String, CollectionConfig> collections,
        @JsonProperty("chat-models") Map<String, ModelConfig> chatModels,
        @JsonProperty("embedding-model") ModelConfig embeddingModel,
        @JsonProperty("reranking-model") ModelConfig rerankingModel,
        Map<String, AgentConfig> agents,
        @JsonProperty("chat-service") ChatServiceConfig chatService,
        LoggingConfig logging) {

    public record TrackRootConfig(
            String id,
            String path,
            @JsonProperty("allowed-source-types") List<SourceType> allowedSourceTypes) {
    }

    public record CollectionConfig(@JsonProperty("track-roots") List<String> trackRoots) {
    }

    public record ModelConfig(String name, String provider) {
    }

    public record AgentConfig(
            @JsonProperty("chat-model") String chatModelName,
            ModelConfig chatModelProperties,
            String collection) {
    }

    public record ChatServiceConfig(
            @JsonProperty("chat-model") String chatModelName,
            ModelConfig chatModelProperties) {
    }

    public record LoggingConfig(@JsonProperty("review-output-dir") String reviewOutputDir) {
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "trackRoots",
                trackRoots,
                "collections",
                collections,
                "chatModels",
                chatModels,
                "embeddingModel",
                embeddingModel,
                "rerankingModel",
                rerankingModel,
                "agents",
                agents,
                "chatService",
                chatService,
                "logging",
                logging);
    }
}
