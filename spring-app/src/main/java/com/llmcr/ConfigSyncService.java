package com.llmcr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.llmcr.config.ApplicationProperties;
import com.llmcr.config.ApplicationProperties.CollectionProperties;
import com.llmcr.config.ApplicationProperties.TrackRootProperties;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.service.ChatService;
import com.llmcr.vectorstore.MyVectorStore;

import jakarta.transaction.Transactional;

/**
 * This class is responsible for initializing the database with the track roots
 * and collections defined in the configuration.
 * This will be run every time the application starts.
 */
@Component
public class ConfigSyncService {

    private final TrackRootRepository trackRootRepository;
    private final ChunkCollectionRepository chunkCollectionRepository;
    private final ApplicationProperties properties;
    private final MyVectorStore vectorStore;

    public ConfigSyncService(
            ApplicationProperties properties,
            TrackRootRepository trackRootRepository,
            ChunkCollectionRepository chunkCollectionRepository,
            MyVectorStore vectorStore) {
        this.trackRootRepository = trackRootRepository;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.properties = properties;
        this.vectorStore = vectorStore;
    }

    /**
     * Sync the TrackRoots in the database with the configuration.
     */
    @Transactional
    public void syncTrackRoots() {
        Set<String> configuredPaths = properties.getTrackRoots().values().stream()
                .map(TrackRootProperties::getPath)
                .collect(Collectors.toSet());

        for (TrackRoot trackRoot : trackRootRepository.findAll()) {
            boolean existsInConfig = configuredPaths.contains(trackRoot.getPath());
            if (!existsInConfig) {
                // in database but not in config, delete it
                trackRootRepository.delete(trackRoot);
            }
        }

        for (TrackRootProperties configuredTrackRoot : properties.getTrackRoots().values()) {
            TrackRoot existing = trackRootRepository.findByPath(configuredTrackRoot.getPath());
            if (existing != null) {
                // already exists, updated allowed source types
                // we don't need to add/remove sources here, it will be handled by SyncService
                existing.setAllowedSourceTypes(new HashSet<>(configuredTrackRoot.getAllowedSourceTypes()));
                trackRootRepository.save(existing);
                continue;
            }

            // in config but not in database, create it
            TrackRoot trackRoot = new TrackRoot(configuredTrackRoot.getPath(),
                    new HashSet<>(configuredTrackRoot.getAllowedSourceTypes()));
            trackRootRepository.save(trackRoot);
        }
    }

    /**
     * Sync the ChunkCollections in the database with the configuration.
     */
    @Transactional
    public void syncConfiguredCollections() {
        syncDefaultCollections();

        for (ChunkCollection collection : chunkCollectionRepository.findAll()) {
            boolean existsInConfig = properties.getCollections().containsKey(collection.getName());
            if (!existsInConfig &&
                    !collection.getName().equals(ChatService.COLLECTION_NAME) && !collection.getName().equals("all")) {
                // in database but not in config, delete it
                chunkCollectionRepository.delete(collection);
                vectorStore.removeCollection(collection.getName());
            }
        }

        Map<String, TrackRoot> trackRootsByName = new HashMap<>();
        for (Map.Entry<String, TrackRootProperties> entry : properties.getTrackRoots().entrySet()) {
            String path = entry.getValue().getPath();
            TrackRoot trackRoot = trackRootRepository.findByPath(path);
            if (trackRoot != null) {
                trackRootsByName.put(entry.getKey(), trackRoot);
            }
        }

        for (Map.Entry<String, CollectionProperties> entry : properties.getCollections().entrySet()) {
            String collectionName = entry.getKey();
            CollectionProperties configuredCollection = entry.getValue();
            Set<TrackRoot> targetTrackRoots = configuredCollection.getTrackRoots().stream()
                    .map(trackRootsByName::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            ChunkCollection existing = chunkCollectionRepository.findByName(collectionName).orElse(null);
            if (existing != null) {
                // already exists, update track roots
                existing.clearTrackRoots();
                existing.addTrackRoots(targetTrackRoots);
                chunkCollectionRepository.save(existing);
                continue;
            }

            // in config but not in database, create it
            ChunkCollection chunkCollection = new ChunkCollection(collectionName, targetTrackRoots);
            chunkCollectionRepository.save(chunkCollection);
        }
    }

    private void syncDefaultCollections() {
        Set<TrackRoot> allTrackRoots = new HashSet<>(trackRootRepository.findAll());

        ChunkCollection allCollection = chunkCollectionRepository.findByName("all").orElse(null);
        if (allCollection == null) {
            chunkCollectionRepository.save(new ChunkCollection("all", allTrackRoots));
        } else {
            allCollection.addTrackRoots(allTrackRoots);
            chunkCollectionRepository.save(allCollection);
        }

        ChunkCollection chatCollection = chunkCollectionRepository.findByName(ChatService.COLLECTION_NAME).orElse(null);
        if (chatCollection == null) {
            chunkCollectionRepository.save(new ChunkCollection(ChatService.COLLECTION_NAME, allTrackRoots));
        }
    }
}
