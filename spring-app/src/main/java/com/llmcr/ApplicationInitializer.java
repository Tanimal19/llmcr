package com.llmcr;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.llmcr.service.etl.LoadService;
import com.llmcr.service.sync.ConfigSyncService;

@Component
public class ApplicationInitializer {

    private final ConfigSyncService configSyncService;
    private final LoadService loadService;

    public ApplicationInitializer(
            ConfigSyncService configSyncService,
            LoadService loadService) {
        this.configSyncService = configSyncService;
        this.loadService = loadService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        boolean changed = false;
        changed = configSyncService.syncTrackRoots();
        changed = configSyncService.syncConfiguredCollections();
        if (changed) {
            // If there is any change in track roots or collections, we need to reload all
            // chunks to update the collection-chunk mapping in the vector store.
            loadService.rebuildCollectionChunkMapping();
            loadService.reloadAllChunks();
        }
    }
}
