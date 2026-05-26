package com.llmcr.config.provider;

import org.springframework.stereotype.Component;

import com.llmcr.config.SystemConfig;

@Component
public class EmbeddingModelConfigProvider {
    private final SystemConfig config;

    public EmbeddingModelConfigProvider(SystemConfig config) {
        this.config = config;
    }

    public SystemConfig.ModelConfig getEmbeddingModelConfig() {
        return config.embeddingModel();
    }
}
