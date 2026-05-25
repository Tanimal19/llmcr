package com.llmcr.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.llmcr.entity.Source.SourceType;
import java.util.List;
import java.util.Map;

public record ApplicationProperties(
    @JsonProperty("track-roots") Map<String, TrackRootProperties> trackRoots,
    Map<String, CollectionProperties> collections,
    @JsonProperty("chat-models") Map<String, ModelProperties> chatModels,
    @JsonProperty("embedding-model") ModelProperties embeddingModel,
    @JsonProperty("reranking-model") ModelProperties rerankingModel,
    Map<String, AgentProperties> agents,
    LoggingProperties logging
) {
    public Map<String, TrackRootProperties> getTrackRoots() {
        return trackRoots;
    }

    public Map<String, CollectionProperties> getCollections() {
        return collections;
    }

    public Map<String, ModelProperties> getChatModels() {
        return chatModels;
    }

    public ModelProperties getEmbeddingModel() {
        return embeddingModel;
    }

    public ModelProperties getRerankingModel() {
        return rerankingModel;
    }

    public Map<String, AgentProperties> getAgents() {
        return agents;
    }

    public LoggingProperties getLogging() {
        return logging;
    }

    public record TrackRootProperties(
        String id,
        String path,
        @JsonProperty("allowed-source-types") List<SourceType> allowedSourceTypes
    ) {
        public String getId() {
            return id;
        }

        public String getPath() {
            return path;
        }

        public List<SourceType> getAllowedSourceTypes() {
            return allowedSourceTypes;
        }
    }

    public record CollectionProperties(@JsonProperty("track-roots") List<String> trackRoots) {
        public List<String> getTrackRoots() {
            return trackRoots;
        }
    }

    public record ModelProperties(String name, String provider) {
        public String getName() {
            return name;
        }

        public String getProvider() {
            return provider;
        }
    }

    public record AgentProperties(
        @JsonProperty("chat-model") String chat,
        ModelProperties chatModelProperties,
        String collection
    ) {
        public String getChat() {
            return chat;
        }

        public ModelProperties getChatModelProperties() {
            return chatModelProperties;
        }

        public String getCollection() {
            return collection;
        }
    }

    public record LoggingProperties(@JsonProperty("review-output-dir") String reviewOutputDir) {
        public String getReviewOutputDir() {
            return reviewOutputDir;
        }
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
            "logging",
            logging
        );
    }
}
