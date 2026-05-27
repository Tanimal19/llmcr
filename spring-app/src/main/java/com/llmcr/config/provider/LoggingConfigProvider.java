package com.llmcr.config.provider;

import com.llmcr.config.SystemConfig;
import org.springframework.stereotype.Component;

@Component
public class LoggingConfigProvider {
  private final SystemConfig config;

  public LoggingConfigProvider(SystemConfig config) {
    this.config = config;
  }

  public String getReviewOutputDirectory() {
    return config.logging().reviewOutputDir();
  }
}
