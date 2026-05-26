package com.llmcr.feature.sync.trackroot;

import com.llmcr.config.SystemConfig.TrackRootConfig;
import com.llmcr.config.provider.TrackRootConfigProvider;
import com.llmcr.domain.entity.Source.SourceType;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.repository.TrackRootRepository;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TrackRootConfigSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger(TrackRootConfigSynchronizer.class);

    private final TrackRootRepository trackRootRepository;
    private final TrackRootConfigProvider trackRootConfigProvider;

    public TrackRootConfigSynchronizer(TrackRootRepository trackRootRepository,
            TrackRootConfigProvider trackRootConfigProvider) {
        this.trackRootRepository = trackRootRepository;
        this.trackRootConfigProvider = trackRootConfigProvider;
    }

    public boolean syncTrackRoots() {
        boolean changed = removeUnconfiguredTrackRoots();
        changed |= upsertConfiguredTrackRoots();
        return changed;
    }

    private boolean removeUnconfiguredTrackRoots() {
        Set<String> configuredPaths = trackRootConfigProvider.getAllConfiguredTrackRootPaths();
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

    private boolean upsertConfiguredTrackRoots() {
        boolean changed = false;
        for (TrackRootConfig configuredTrackRoot : trackRootConfigProvider.getAllConfiguredTrackRoots().values()) {
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
