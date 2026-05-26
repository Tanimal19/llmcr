package com.llmcr.config.provider;

import org.springframework.stereotype.Component;

import com.llmcr.config.SystemConfig;

@Component
public class AgentConfigProvider {
    private final SystemConfig config;

    public AgentConfigProvider(SystemConfig config) {
        this.config = config;
    }

    public SystemConfig.ModelConfig getAgentChatModelConfig(String agentName) {
        return config.agents().get(agentName).chatModelProperties();
    }

    public String getAgentCollectionConfig(String agentName) {
        return config.agents().get(agentName).collection();
    }
}
