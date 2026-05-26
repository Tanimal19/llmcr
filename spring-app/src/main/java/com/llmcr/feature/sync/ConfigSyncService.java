package com.llmcr.feature.sync;

import com.llmcr.domain.exception.APIServiceException;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for initializing the database with the track roots
 * and collections defined in the configuration.
 * This will be run every time the application starts.
 */
@Component
public class ConfigSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigSyncService.class);

    private final TrackRootConfigSynchronizer trackRootSynchronizer;
    private final CollectionConfigSynchronizer collectionSynchronizer;

    public ConfigSyncService(
            TrackRootConfigSynchronizer trackRootSynchronizer,
            CollectionConfigSynchronizer collectionSynchronizer) {
        this.trackRootSynchronizer = trackRootSynchronizer;
        this.collectionSynchronizer = collectionSynchronizer;
    }

    /**
     * Sync the TrackRoots in the database with the configuration.
     */
    @Transactional
    public boolean syncTrackRoots() {
        logger.info("trackRoots:start");
        try {
            boolean changed = trackRootSynchronizer.syncTrackRoots();

            logger.info("trackRoots:done changed={}", changed);
            return changed;
        } catch (Exception ex) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.CONFIG_SYNC_TRACK_ROOTS_FAILED,
                    "Failed to sync track roots",
                    ex);
        }
    }

    /**
     * Sync the ChunkCollections in the database with the configuration.
     */
    @Transactional
    public boolean syncConfiguredCollections() {
        logger.info("collections:start");
        try {
            boolean changed = collectionSynchronizer.syncCollections();

            logger.info("collections:done changed={}", changed);
            return changed;
        } catch (APIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.CONFIG_SYNC_COLLECTIONS_FAILED,
                    "Failed to sync collections",
                    ex);
        }
    }

}
