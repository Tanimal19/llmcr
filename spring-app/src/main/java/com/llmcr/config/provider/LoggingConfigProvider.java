package com.llmcr.config.provider;

import com.llmcr.config.SystemConfig;

public class LoggingConfigProvider {
    private final SystemConfig config;

    public LoggingConfigProvider(SystemConfig config) {
        this.config = config;
    }

    public String getReviewOutputDirectory() {
        return config.logging().reviewOutputDir();
    }
}
