package com.llmcr.config.provider;

import org.springframework.stereotype.Component;

import com.llmcr.config.SystemConfig;

@Component
public class RerankingModelConfigProvider {
    private final SystemConfig config;

    public RerankingModelConfigProvider(SystemConfig config) {
        this.config = config;
    }

    public SystemConfig.ModelConfig getRerankingModelConfig() {
        return config.rerankingModel();
    }
}
