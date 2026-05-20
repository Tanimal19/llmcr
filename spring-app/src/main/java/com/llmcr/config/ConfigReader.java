package com.llmcr.config;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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
            JsonNode root = objectMapper.readTree(new File(configFilePath));
            ApplicationProperties config = objectMapper.treeToValue(root, ApplicationProperties.class);
            bindAgentChatModelConfig(root, config);
            return config;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read config: " + configFilePath, e);
        }
    }

    public void save(ApplicationProperties config) {
        try {
            objectMapper.writeValue(new File(configFilePath), toYamlMap(config));
        } catch (IOException e) {
            throw new RuntimeException("Unable to save config: " + configFilePath, e);
        }
    }

    private void bindAgentChatModelConfig(JsonNode root, ApplicationProperties config) {
        JsonNode agentsNode = root.path("agents");
        if (!agentsNode.isObject()) {
            return;
        }

        Map<String, ApplicationProperties.AgentProperties> agents = config.getAgents();
        if (agents == null) {
            agents = new LinkedHashMap<>();
            config.setAgents(agents);
        }

        Map<String, ApplicationProperties.ModelProperties> chatModels = config.getChatModels();
        if (chatModels == null) {
            chatModels = Map.of();
        }

        for (Map.Entry<String, JsonNode> agentEntry : agentsNode.properties()) {
            String agentName = agentEntry.getKey();
            JsonNode agentNode = agentEntry.getValue();

            JsonNode chatNode = agentNode.path("chat");
            if (!chatNode.isTextual()) {
                continue;
            }

            String modelKey = chatNode.asText();
            ApplicationProperties.ModelProperties modelConfig = chatModels.get(modelKey);
            if (modelConfig == null) {
                continue;
            }

            ApplicationProperties.AgentProperties agentConfig = agents.computeIfAbsent(
                    agentName,
                    key -> new ApplicationProperties.AgentProperties());
            agentConfig.setChatModelProperties(modelConfig);
        }
    }

    private Map<String, Object> toYamlMap(ApplicationProperties config) {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("track-roots", config.getTrackRoots());
        root.put("collections", config.getCollections());
        root.put("chat-models", config.getChatModels());
        root.put("embedding-model", config.getEmbeddingModel());
        root.put("reranking-model", config.getRerankingModel());
        root.put("agents", buildAgentsYaml(config));
        root.put("logging", config.getLogging());

        return root;
    }

    private Map<String, Object> buildAgentsYaml(ApplicationProperties config) {
        Map<String, Object> agentsYaml = new LinkedHashMap<>();
        Map<String, ApplicationProperties.AgentProperties> agents = config.getAgents();
        if (agents == null) {
            return agentsYaml;
        }

        for (Map.Entry<String, ApplicationProperties.AgentProperties> entry : agents.entrySet()) {
            String agentName = entry.getKey();
            ApplicationProperties.AgentProperties agentConfig = entry.getValue();
            Map<String, Object> agentYaml = new LinkedHashMap<>();

            String modelKey = resolveModelKey(config.getChatModels(), agentConfig.getChatModelProperties());
            if (modelKey != null) {
                agentYaml.put("chat", modelKey);
            }

            String collection = agentConfig.getCollection();
            if (collection != null) {
                agentYaml.put("collection", collection);
            }

            agentsYaml.put(agentName, agentYaml);
        }

        return agentsYaml;
    }

    private String resolveModelKey(
            Map<String, ApplicationProperties.ModelProperties> models,
            ApplicationProperties.ModelProperties targetModel) {
        if (models == null || targetModel == null) {
            return null;
        }

        for (Map.Entry<String, ApplicationProperties.ModelProperties> entry : models.entrySet()) {
            if (entry.getValue() == targetModel) {
                return entry.getKey();
            }
        }

        String targetName = targetModel.getName();
        if (targetName == null) {
            return null;
        }

        if (models.containsKey(targetName)) {
            return targetName;
        }

        for (Map.Entry<String, ApplicationProperties.ModelProperties> entry : models.entrySet()) {
            ApplicationProperties.ModelProperties model = entry.getValue();
            if (targetName.equals(model.getName())) {
                return entry.getKey();
            }
        }

        return null;
    }
}
