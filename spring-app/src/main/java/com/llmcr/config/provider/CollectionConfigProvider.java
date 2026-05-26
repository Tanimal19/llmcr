package com.llmcr.config.provider;

import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.llmcr.config.SystemConfig;

@Component
public class CollectionConfigProvider {
    private final SystemConfig config;

    public CollectionConfigProvider(SystemConfig config) {
        this.config = config;
    }

    public Collection<SystemConfig.CollectionConfig> getAllConfiguredCollections() {
        return config.collections().values();
    }

    public Set<String> getAllConfiguredCollectionNames() {
        return config.collections().keySet();
    }
}
