package com.llmcr.config.provider;

import com.llmcr.config.SystemConfig;
import org.springframework.stereotype.Component;

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
