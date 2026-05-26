package com.llmcr.config.provider;

import com.llmcr.config.SystemConfig;

public class RerankingModelConfigProvider {
    private final SystemConfig config;

    public RerankingModelConfigProvider(SystemConfig config) {
        this.config = config;
    }

    public SystemConfig.ModelConfig getRerankingModelConfig() {
        return config.rerankingModel();
    }
}
