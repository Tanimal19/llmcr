package com.llmcr.service.sync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.config.ApplicationProperties;
import com.llmcr.config.ApplicationProperties.CollectionProperties;
import com.llmcr.config.ApplicationProperties.TrackRootProperties;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.TrackRoot;
import com.llmcr.entity.Source.SourceType;
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

    private static final Logger logger = LoggerFactory.getLogger(ConfigSyncService.class);

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
    public boolean syncTrackRoots() {
        logger.info("Syncing track roots with configuration...");
        boolean changed = false;

        Set<String> configuredPaths = properties.getTrackRoots().values().stream()
                .map(TrackRootProperties::getPath)
                .collect(Collectors.toSet());

        for (TrackRoot trackRoot : trackRootRepository.findAll()) {
            boolean existsInConfig = configuredPaths.contains(trackRoot.getPath());
            if (!existsInConfig) {
                // in database but not in config, delete it
                trackRootRepository.delete(trackRoot);
                changed = true;
                logger.info("Deleted track root: {}", trackRoot.getPath());
            }
        }

        for (TrackRootProperties configuredTrackRoot : properties.getTrackRoots().values()) {
            TrackRoot existing = trackRootRepository.findByPath(configuredTrackRoot.getPath());
            if (existing != null) {
                // already exists, updated allowed source types
                // we don't need to add/remove sources here, it will be handled by SyncService
                Set<SourceType> existingTypes = existing.getAllowedSourceTypes();
                if (existing.getAllowedSourceTypes().equals(existingTypes)) {
                    continue;
                }
                existing.setAllowedSourceTypes(existingTypes);
                trackRootRepository.save(existing);
                changed = true;
                logger.info("Updated track root: {}", existing.getPath());
            } else {
                // in config but not in database, create it
                TrackRoot trackRoot = new TrackRoot(configuredTrackRoot.getPath(),
                        new HashSet<>(configuredTrackRoot.getAllowedSourceTypes()));
                trackRootRepository.save(trackRoot);
                changed = true;
                logger.info("Created track root: {}", trackRoot.getPath());
            }
        }

        return changed;
    }

    /**
     * Sync the ChunkCollections in the database with the configuration.
     */
    @Transactional
    public boolean syncConfiguredCollections() {
        logger.info("Syncing configured collections with configuration...");
        syncDefaultCollections();
        boolean changed = false;

        for (ChunkCollection collection : chunkCollectionRepository.findAll()) {
            boolean existsInConfig = properties.getCollections().containsKey(collection.getName());
            if (!existsInConfig &&
                    !collection.getName().equals(ChatService.COLLECTION_NAME) && !collection.getName().equals("all")) {
                // in database but not in config, delete it
                chunkCollectionRepository.delete(collection);
                vectorStore.removeCollection(collection.getName());
                changed = true;
                logger.info("Deleted chunk collection: {}", collection.getName());
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
                if (existing.getTrackRoots().equals(targetTrackRoots)) {
                    continue;
                }
                existing.clearTrackRoots();
                existing.addTrackRoots(targetTrackRoots);
                chunkCollectionRepository.save(existing);
                changed = true;
                logger.info("Updated chunk collection: {}", existing.getName());
            } else {
                // in config but not in database, create it
                ChunkCollection chunkCollection = new ChunkCollection(collectionName, targetTrackRoots);
                chunkCollectionRepository.save(chunkCollection);
                changed = true;
                logger.info("Created chunk collection: {}", chunkCollection.getName());
            }
        }

        return changed;
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
