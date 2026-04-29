package com.llmcr.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Source.SourceType;

@Component
@ConfigurationProperties(prefix = "app.datasets")
public class DatabaseInitProperties {

    private List<TrackRootConfig> trackRoots = new ArrayList<>();
    private List<CollectionConfig> collections = new ArrayList<>();

    public List<TrackRootConfig> getTrackRoots() {
        return trackRoots;
    }

    public void setTrackRoots(List<TrackRootConfig> trackRoots) {
        this.trackRoots = trackRoots;
    }

    public List<CollectionConfig> getCollections() {
        return collections;
    }

    public void setCollections(List<CollectionConfig> collections) {
        this.collections = collections;
    }

    public static class TrackRootConfig {
        private String id;
        private String path;
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

    public static class CollectionConfig {
        private String name;
        private List<String> trackRoots = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTrackRoots() {
            return trackRoots;
        }

        public void setTrackRoots(List<String> trackRoots) {
            this.trackRoots = trackRoots;
        }
    }
}
