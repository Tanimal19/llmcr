package com.llmcr.config.provider;

import com.llmcr.config.SystemConfig;
import org.springframework.stereotype.Component;

@Component
public class ChatServiceConfigProvider {
  private final SystemConfig config;

  public ChatServiceConfigProvider(SystemConfig config) {
    this.config = config;
  }

  public SystemConfig.ModelConfig getChatServiceModelConfig() {
    return config.chatService().chatModelProperties();
  }
}
