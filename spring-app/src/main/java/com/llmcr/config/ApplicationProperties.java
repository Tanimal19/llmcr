package com.llmcr.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.llmcr.entity.Source.SourceType;

public class ApplicationProperties {

    @JsonProperty("track-roots")
    private List<TrackRootProperties> trackRoots = new ArrayList<>();

    private Map<String, CollectionProperties> collections = new LinkedHashMap<>();

    @JsonProperty("chat-models")
    private Map<String, ModelProperties> chatModels = new LinkedHashMap<>();

    @JsonProperty("embedding-model")
    private ModelProperties embeddingModel = new ModelProperties();

    @JsonProperty("reranking-model")
    private ModelProperties rerankingModel = new ModelProperties();

    private Map<String, AgentProperties> agents = new LinkedHashMap<>();

    private LoggingProperties logging = new LoggingProperties();

    // -------------------------------------------------------------------------

    public List<TrackRootProperties> getTrackRoots() {
        return trackRoots;
    }

    public void setTrackRoots(List<TrackRootProperties> trackRoots) {
        this.trackRoots = trackRoots;
    }

    public Map<String, CollectionProperties> getCollections() {
        return collections;
    }

    public void setCollections(Map<String, CollectionProperties> collections) {
        this.collections = collections;
    }

    public Map<String, ModelProperties> getChatModels() {
        return chatModels;
    }

    public void setChatModels(Map<String, ModelProperties> chatModels) {
        this.chatModels = chatModels;
    }

    public ModelProperties getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(ModelProperties embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public ModelProperties getRerankingModel() {
        return rerankingModel;
    }

    public void setRerankingModel(ModelProperties rerankingModel) {
        this.rerankingModel = rerankingModel;
    }

    public Map<String, AgentProperties> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, AgentProperties> agents) {
        this.agents = agents;
    }

    public LoggingProperties getLogging() {
        return logging;
    }

    public void setLogging(LoggingProperties logging) {
        this.logging = logging;
    }

    // -------------------------------------------------------------------------
    // Nested Properties classes
    // -------------------------------------------------------------------------

    public static class TrackRootProperties {

        private String id;
        private String path;

        @JsonProperty("allowed-source-types")
        private List<SourceType> allowedSourceTypes = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<SourceType> getAllowedSourceTypes() {
            return allowedSourceTypes;
        }

        public void setAllowedSourceTypes(List<SourceType> allowedSourceTypes) {
            this.allowedSourceTypes = allowedSourceTypes;
        }
    }

    public static class CollectionProperties {

        @JsonProperty("track-roots")
        private List<String> trackRoots = new ArrayList<>();

        public List<String> getTrackRoots() {
            return trackRoots;
        }

        public void setTrackRoots(List<String> trackRoots) {
            this.trackRoots = trackRoots;
        }
    }

    public static class ModelProperties {

        private String name;
        private String provider;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    public static class AgentProperties {

        private ModelProperties chatModelProperties;
        private String collection;

        public ModelProperties getChatModelProperties() {
            return chatModelProperties;
        }

        public void setChatModelProperties(ModelProperties chatModelProperties) {
            this.chatModelProperties = chatModelProperties;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }
    }

    public static class LoggingProperties {

        @JsonProperty("review-output-dir")
        private String reviewOutputDir;

        public String getReviewOutputDir() {
            return reviewOutputDir;
        }

        public void setReviewOutputDir(String reviewOutputDir) {
            this.reviewOutputDir = reviewOutputDir;
        }
    }
}
