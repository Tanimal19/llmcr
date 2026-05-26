package com.llmcr.config.provider;

import org.springframework.stereotype.Component;

import com.llmcr.config.SystemConfig;

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
