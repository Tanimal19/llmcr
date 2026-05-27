package com.llmcr.config.provider;

import com.llmcr.config.SystemConfig;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TrackRootConfigProvider {
  private final SystemConfig config;

  public TrackRootConfigProvider(SystemConfig config) {
    this.config = config;
  }

  public Map<String, SystemConfig.TrackRootConfig> getAllConfiguredTrackRoots() {
    return config.trackRoots();
  }

  public Set<String> getAllConfiguredTrackRootPaths() {
    return config.trackRoots().values().stream()
        .map(SystemConfig.TrackRootConfig::path)
        .collect(Collectors.toSet());
  }
}
