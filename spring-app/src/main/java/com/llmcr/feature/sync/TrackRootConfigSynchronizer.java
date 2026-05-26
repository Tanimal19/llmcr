package com.llmcr.feature.sync;

import com.llmcr.config.SystemConfig;
import com.llmcr.config.SystemConfig.TrackRootConfig;
import com.llmcr.domain.entity.Source.SourceType;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.repository.TrackRootRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TrackRootConfigSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger(TrackRootConfigSynchronizer.class);

    private final TrackRootRepository trackRootRepository;

    public TrackRootConfigSynchronizer(TrackRootRepository trackRootRepository) {
        this.trackRootRepository = trackRootRepository;
    }

    public boolean syncTrackRoots(SystemConfig properties) {
        Set<String> configuredPaths = properties
                .trackRoots()
                .values()
                .stream()
                .map(TrackRootConfig::path)
                .collect(Collectors.toSet());

        boolean changed = removeUnconfiguredTrackRoots(configuredPaths);
        changed |= upsertConfiguredTrackRoots(properties);
        return changed;
    }

    private boolean removeUnconfiguredTrackRoots(Set<String> configuredPaths) {
        boolean changed = false;
        for (TrackRoot trackRoot : trackRootRepository.findAll()) {
            boolean existsInConfig = configuredPaths.contains(trackRoot.getPath());
            if (!existsInConfig) {
                trackRootRepository.delete(trackRoot);
                changed = true;
                logger.info("trackRoot:deleted path={}", trackRoot.getPath());
            }
        }
        return changed;
    }

    private boolean upsertConfiguredTrackRoots(SystemConfig properties) {
        boolean changed = false;
        for (TrackRootConfig configuredTrackRoot : properties.trackRoots().values()) {
            TrackRoot existing = trackRootRepository.findByPath(configuredTrackRoot.path());
            Set<SourceType> configuredTypes = new HashSet<>(configuredTrackRoot.allowedSourceTypes());
            if (existing != null) {
                if (existing.getAllowedSourceTypes().equals(configuredTypes)) {
                    continue;
                }
                existing.setAllowedSourceTypes(configuredTypes);
                trackRootRepository.save(existing);
                changed = true;
                logger.info("trackRoot:updated path={}", existing.getPath());
            } else {
                TrackRoot trackRoot = new TrackRoot(configuredTrackRoot.path(), configuredTypes);
                trackRootRepository.save(trackRoot);
                changed = true;
                logger.info("trackRoot:created path={}", trackRoot.getPath());
            }
        }
        return changed;
    }
}
