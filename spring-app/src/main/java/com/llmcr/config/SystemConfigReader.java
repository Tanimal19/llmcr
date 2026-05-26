package com.llmcr.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.llmcr.domain.entity.Source.SourceType;
import com.llmcr.domain.util.PathUtils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for reading the system configuration from a YAML
 * file and providing it as a Spring Bean.
 */
@Component
public class SystemConfigReader {

    private static final SystemConfig.ModelConfig EMPTY_MODEL = new SystemConfig.ModelConfig(null, null);
    private static final SystemConfig.LoggingConfig EMPTY_LOGGING = new SystemConfig.LoggingConfig(null);
    private static final SystemConfig.ChatServiceConfig EMPTY_CHAT_SERVICE =
            new SystemConfig.ChatServiceConfig(null, EMPTY_MODEL);

    private final String configFilePath;
    private final ObjectMapper objectMapper;

    public SystemConfigReader(@Value("${config.path}") String configFilePath) {
        this.configFilePath = configFilePath;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    @Bean
    public SystemConfig applicationConfiguration() {
        try {
            SystemConfig raw = objectMapper.readValue(new File(configFilePath), SystemConfig.class);
            return normalize(raw);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read config: " + configFilePath, e);
        }
    }

    private SystemConfig normalize(SystemConfig raw) {
        Map<String, SystemConfig.ModelConfig> chatModels = normalizeChatModels(raw.chatModels());
        SystemConfig.ChatServiceConfig chatService = normalizeChatService(raw.chatService(), chatModels);
        return new SystemConfig(
                normalizeTrackRoots(raw.trackRoots()),
                normalizeCollections(raw.collections()),
                chatModels,
                normalizeModel(raw.embeddingModel()),
                normalizeModel(raw.rerankingModel()),
            normalizeAgents(raw.agents(), chatModels),
                chatService,
                normalizeLogging(raw.logging()));
    }

    private Map<String, SystemConfig.TrackRootConfig> normalizeTrackRoots(
            Map<String, SystemConfig.TrackRootConfig> trackRoots) {
        return mapValues(trackRoots, (id, value) -> {
            String path = value == null ? null : PathUtils.toRelativePath(value.path());
            List<SourceType> allowedSourceTypes = value == null ? List.of() : defaultList(value.allowedSourceTypes());
            return new SystemConfig.TrackRootConfig(id, path, allowedSourceTypes);
        });
    }

    private Map<String, SystemConfig.CollectionConfig> normalizeCollections(
            Map<String, SystemConfig.CollectionConfig> collections) {
        return mapValues(collections, (id, value) -> {
            List<String> trackRoots = value == null ? List.of() : defaultList(value.trackRoots());
            return new SystemConfig.CollectionConfig(trackRoots);
        });
    }

    private Map<String, SystemConfig.ModelConfig> normalizeChatModels(
            Map<String, SystemConfig.ModelConfig> chatModels) {
        return mapValues(chatModels, (id, model) -> normalizeModel(model));
    }

    private Map<String, SystemConfig.AgentConfig> normalizeAgents(
            Map<String, SystemConfig.AgentConfig> agents,
            Map<String, SystemConfig.ModelConfig> chatModels) {
        return mapValues(agents, (id, value) -> {
            String chatModelKey = value == null ? null : value.chatModelName();
            SystemConfig.ModelConfig modelProperties = chatModels.getOrDefault(chatModelKey, EMPTY_MODEL);
            return new SystemConfig.AgentConfig(
                    chatModelKey,
                    modelProperties,
                    value == null ? null : value.collection());
        });
    }

    private SystemConfig.ChatServiceConfig normalizeChatService(
            SystemConfig.ChatServiceConfig chatService,
            Map<String, SystemConfig.ModelConfig> chatModels) {
        if (chatService == null) {
            return EMPTY_CHAT_SERVICE;
        }

        String chatModelKey = chatService.chatModelName();
        SystemConfig.ModelConfig modelProperties = chatModels.getOrDefault(chatModelKey, EMPTY_MODEL);
        return new SystemConfig.ChatServiceConfig(chatModelKey, modelProperties);
    }

    private <T, R> Map<String, R> mapValues(Map<String, T> source, BiFunction<String, T, R> mapper) {
        Map<String, R> normalized = new LinkedHashMap<>();
        defaultMap(source).forEach((key, value) -> normalized.put(key, mapper.apply(key, value)));
        return Map.copyOf(normalized);
    }

    private SystemConfig.LoggingConfig normalizeLogging(SystemConfig.LoggingConfig logging) {
        return logging == null ? EMPTY_LOGGING : logging;
    }

    private SystemConfig.ModelConfig normalizeModel(SystemConfig.ModelConfig model) {
        return model == null ? EMPTY_MODEL : model;
    }

    private <T> Map<String, T> defaultMap(Map<String, T> value) {
        return value == null ? Map.of() : value;
    }

    private <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
