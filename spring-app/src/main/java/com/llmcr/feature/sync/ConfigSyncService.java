package com.llmcr.feature.sync;

import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.feature.sync.trackroot.TrackRootConfigSynchronizer;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfigSyncService {

  private static final Logger logger = LoggerFactory.getLogger(ConfigSyncService.class);

  private final TrackRootConfigSynchronizer trackRootSynchronizer;

  public ConfigSyncService(TrackRootConfigSynchronizer trackRootSynchronizer) {
    this.trackRootSynchronizer = trackRootSynchronizer;
  }

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
}
