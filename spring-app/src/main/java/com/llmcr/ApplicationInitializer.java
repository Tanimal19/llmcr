package com.llmcr;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.llmcr.service.etl.LoadService;

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
        configSyncService.syncTrackRoots();
        configSyncService.syncConfiguredCollections();
        loadService.reloadAllChunks();
    }
}
