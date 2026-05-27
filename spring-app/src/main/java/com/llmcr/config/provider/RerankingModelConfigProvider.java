package com.llmcr.config.provider;

import com.llmcr.config.SystemConfig;
import org.springframework.stereotype.Component;

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
