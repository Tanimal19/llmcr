package com.llmcr.config.provider;

import org.springframework.stereotype.Component;

import com.llmcr.config.SystemConfig;

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
