package com.llmcr.sync;

import com.llmcr.ChatService;
import com.llmcr.api.APIServiceException;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.config.ApplicationProperties.CollectionProperties;
import com.llmcr.config.ApplicationProperties.TrackRootProperties;
import com.llmcr.database.entity.ChunkCollection;
import com.llmcr.database.entity.TrackRoot;
import com.llmcr.database.entity.Source.SourceType;
import com.llmcr.database.repository.ChunkCollectionRepository;
import com.llmcr.database.repository.TrackRootRepository;
import com.llmcr.vectorstore.MyVectorStore;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
    private static final String ALL_COLLECTION_NAME = "all";

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
        logger.info("trackRoots:start");
        try {
            Set<String> configuredPaths = properties
                    .getTrackRoots()
                    .values()
                    .stream()
                    .map(TrackRootProperties::getPath)
                    .collect(Collectors.toSet());
            boolean changed = removeUnconfiguredTrackRoots(configuredPaths);
            changed |= upsertConfiguredTrackRoots();

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
            syncDefaultCollections();
            boolean changed = removeUnconfiguredCollections();

            Map<String, TrackRoot> trackRootsByName = loadTrackRootsByName();
            changed |= upsertConfiguredCollections(trackRootsByName);

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

    private void syncDefaultCollections() {
        try {
            Set<TrackRoot> allTrackRoots = new HashSet<>(trackRootRepository.findAll());

            ChunkCollection allCollection = chunkCollectionRepository.findByName(ALL_COLLECTION_NAME).orElse(null);
            if (allCollection == null) {
                chunkCollectionRepository.save(new ChunkCollection(ALL_COLLECTION_NAME, allTrackRoots));
            } else {
                allCollection.addTrackRoots(allTrackRoots);
                chunkCollectionRepository.save(allCollection);
            }

            ChunkCollection chatCollection = chunkCollectionRepository
                    .findByName(ChatService.COLLECTION_NAME)
                    .orElse(null);
            if (chatCollection == null) {
                chunkCollectionRepository.save(new ChunkCollection(ChatService.COLLECTION_NAME, allTrackRoots));
            }
        } catch (Exception ex) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.CONFIG_SYNC_DEFAULT_COLLECTIONS_FAILED,
                    "Failed to sync default collections",
                    ex);
        }
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

    private boolean upsertConfiguredTrackRoots() {
        boolean changed = false;
        for (TrackRootProperties configuredTrackRoot : properties.getTrackRoots().values()) {
            TrackRoot existing = trackRootRepository.findByPath(configuredTrackRoot.getPath());
            Set<SourceType> configuredTypes = new HashSet<>(configuredTrackRoot.getAllowedSourceTypes());
            if (existing != null) {
                if (existing.getAllowedSourceTypes().equals(configuredTypes)) {
                    continue;
                }
                existing.setAllowedSourceTypes(configuredTypes);
                trackRootRepository.save(existing);
                changed = true;
                logger.info("trackRoot:updated path={}", existing.getPath());
            } else {
                TrackRoot trackRoot = new TrackRoot(configuredTrackRoot.getPath(), configuredTypes);
                trackRootRepository.save(trackRoot);
                changed = true;
                logger.info("trackRoot:created path={}", trackRoot.getPath());
            }
        }
        return changed;
    }

    private boolean removeUnconfiguredCollections() {
        boolean changed = false;
        for (ChunkCollection collection : chunkCollectionRepository.findAll()) {
            boolean existsInConfig = properties.getCollections().containsKey(collection.getName());
            if (!existsInConfig && !isProtectedCollection(collection.getName())) {
                chunkCollectionRepository.delete(collection);
                vectorStore.removeCollection(collection.getName());
                changed = true;
                logger.info("collection:deleted name={}", collection.getName());
            }
        }
        return changed;
    }

    private Map<String, TrackRoot> loadTrackRootsByName() {
        Map<String, TrackRoot> trackRootsByName = new HashMap<>();
        for (Map.Entry<String, TrackRootProperties> entry : properties.getTrackRoots().entrySet()) {
            String path = entry.getValue().getPath();
            TrackRoot trackRoot = trackRootRepository.findByPath(path);
            if (trackRoot != null) {
                trackRootsByName.put(entry.getKey(), trackRoot);
            }
        }
        return trackRootsByName;
    }

    private boolean upsertConfiguredCollections(Map<String, TrackRoot> trackRootsByName) {
        boolean changed = false;
        for (Map.Entry<String, CollectionProperties> entry : properties.getCollections().entrySet()) {
            String collectionName = entry.getKey();
            CollectionProperties configuredCollection = entry.getValue();
            Set<TrackRoot> targetTrackRoots = configuredCollection
                    .getTrackRoots()
                    .stream()
                    .map(trackRootsByName::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            ChunkCollection existing = chunkCollectionRepository.findByName(collectionName).orElse(null);
            if (existing != null) {
                if (existing.getTrackRoots().equals(targetTrackRoots)) {
                    continue;
                }
                existing.clearTrackRoots();
                existing.addTrackRoots(targetTrackRoots);
                chunkCollectionRepository.save(existing);
                changed = true;
                logger.info("collection:updated name={}", existing.getName());
            } else {
                ChunkCollection chunkCollection = new ChunkCollection(collectionName, targetTrackRoots);
                chunkCollectionRepository.save(chunkCollection);
                changed = true;
                logger.info("collection:created name={}", chunkCollection.getName());
            }
        }
        return changed;
    }

    private boolean isProtectedCollection(String collectionName) {
        return ChatService.COLLECTION_NAME.equals(collectionName) || ALL_COLLECTION_NAME.equals(collectionName);
    }
}
