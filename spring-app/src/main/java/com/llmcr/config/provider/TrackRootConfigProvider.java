package com.llmcr.config.provider;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import com.llmcr.config.SystemConfig;

public class TrackRootConfigProvider {
    private final SystemConfig config;

    public TrackRootConfigProvider(SystemConfig config) {
        this.config = config;
    }

    public Collection<SystemConfig.TrackRootConfig> getAllConfiguredTrackRoots() {
        return config.trackRoots().values();
    }

    public Set<String> getAllConfiguredTrackRootPaths() {
        return config.trackRoots().values().stream().map(SystemConfig.TrackRootConfig::path)
                .collect(Collectors.toSet());
    }
}
