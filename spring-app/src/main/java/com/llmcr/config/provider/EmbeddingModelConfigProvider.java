package com.llmcr.config.provider;

import com.llmcr.config.SystemConfig;
import org.springframework.stereotype.Component;

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
