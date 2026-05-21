package com.llmcr.config;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.llmcr.entity.Source.SourceType;

@Component
public class ConfigReader {

    private final String configFilePath;
    private final ObjectMapper objectMapper;

    public ConfigReader(@Value("${config.path}") String configFilePath) {
        this.configFilePath = configFilePath;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public ApplicationProperties applicationConfiguration() {
        try {
            ApplicationProperties raw = objectMapper.readValue(new File(configFilePath), ApplicationProperties.class);
            return normalize(raw);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read config: " + configFilePath, e);
        }
    }

    private ApplicationProperties normalize(ApplicationProperties raw) {
        Map<String, ApplicationProperties.TrackRootProperties> trackRoots = normalizeTrackRoots(raw.trackRoots());
        Map<String, ApplicationProperties.CollectionProperties> collections = normalizeCollections(raw.collections());
        Map<String, ApplicationProperties.ModelProperties> chatModels = normalizeChatModels(raw.chatModels());
        ApplicationProperties.ModelProperties embeddingModel = normalizeModel(raw.embeddingModel());
        ApplicationProperties.ModelProperties rerankingModel = normalizeModel(raw.rerankingModel());
        Map<String, ApplicationProperties.AgentProperties> agents = normalizeAgents(raw.agents(), chatModels);
        ApplicationProperties.LoggingProperties logging = raw.logging() == null
                ? new ApplicationProperties.LoggingProperties(null)
                : raw.logging();

        return new ApplicationProperties(
                trackRoots,
                collections,
                chatModels,
                embeddingModel,
                rerankingModel,
                agents,
                logging);
    }

    private Map<String, ApplicationProperties.TrackRootProperties> normalizeTrackRoots(
            Map<String, ApplicationProperties.TrackRootProperties> trackRoots) {
        Map<String, ApplicationProperties.TrackRootProperties> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ApplicationProperties.TrackRootProperties> entry : defaultMap(trackRoots).entrySet()) {
            String id = entry.getKey();
            ApplicationProperties.TrackRootProperties value = entry.getValue();
            String path = value == null ? null : value.path();
            List<SourceType> allowedSourceTypes = value == null ? List.of() : defaultList(value.allowedSourceTypes());
            normalized.put(id, new ApplicationProperties.TrackRootProperties(id, path, allowedSourceTypes));
        }
        return Map.copyOf(normalized);
    }

    private Map<String, ApplicationProperties.CollectionProperties> normalizeCollections(
            Map<String, ApplicationProperties.CollectionProperties> collections) {
        Map<String, ApplicationProperties.CollectionProperties> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ApplicationProperties.CollectionProperties> entry : defaultMap(collections).entrySet()) {
            ApplicationProperties.CollectionProperties value = entry.getValue();
            List<String> trackRoots = value == null ? List.of() : defaultList(value.trackRoots());
            normalized.put(entry.getKey(), new ApplicationProperties.CollectionProperties(trackRoots));
        }
        return Map.copyOf(normalized);
    }

    private Map<String, ApplicationProperties.ModelProperties> normalizeChatModels(
            Map<String, ApplicationProperties.ModelProperties> chatModels) {
        Map<String, ApplicationProperties.ModelProperties> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ApplicationProperties.ModelProperties> entry : defaultMap(chatModels).entrySet()) {
            normalized.put(entry.getKey(), normalizeModel(entry.getValue()));
        }
        return Map.copyOf(normalized);
    }

    private Map<String, ApplicationProperties.AgentProperties> normalizeAgents(
            Map<String, ApplicationProperties.AgentProperties> agents,
            Map<String, ApplicationProperties.ModelProperties> chatModels) {
        Map<String, ApplicationProperties.AgentProperties> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ApplicationProperties.AgentProperties> entry : defaultMap(agents).entrySet()) {
            ApplicationProperties.AgentProperties value = entry.getValue();
            String chatModelKey = value == null ? null : value.chat();
            ApplicationProperties.ModelProperties modelProperties = chatModels.get(chatModelKey);
            if (modelProperties == null) {
                modelProperties = new ApplicationProperties.ModelProperties(null, null);
            }

            normalized.put(
                    entry.getKey(),
                    new ApplicationProperties.AgentProperties(
                            chatModelKey,
                            modelProperties,
                            value == null ? null : value.collection()));
        }

        return Map.copyOf(normalized);
    }

    private ApplicationProperties.ModelProperties normalizeModel(ApplicationProperties.ModelProperties model) {
        return model == null ? new ApplicationProperties.ModelProperties(null, null) : model;
    }

    private <T> Map<String, T> defaultMap(Map<String, T> value) {
        return value == null ? Map.of() : value;
    }

    private <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
