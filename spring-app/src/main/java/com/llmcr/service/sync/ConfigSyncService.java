package com.llmcr.service.sync;

import com.llmcr.api.APIServiceException;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.config.ApplicationProperties.CollectionProperties;
import com.llmcr.config.ApplicationProperties.TrackRootProperties;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.Source.SourceType;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.service.ChatService;
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
            boolean changed = false;

            Set<String> configuredPaths = properties
                    .getTrackRoots()
                    .values()
                    .stream()
                    .map(TrackRootProperties::getPath)
                    .collect(Collectors.toSet());

            for (TrackRoot trackRoot : trackRootRepository.findAll()) {
                boolean existsInConfig = configuredPaths.contains(trackRoot.getPath());
                if (!existsInConfig) {
                    trackRootRepository.delete(trackRoot);
                    changed = true;
                    logger.info("trackRoot:deleted path={}", trackRoot.getPath());
                }
            }

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
            boolean changed = false;

            for (ChunkCollection collection : chunkCollectionRepository.findAll()) {
                boolean existsInConfig = properties.getCollections().containsKey(collection.getName());
                if (!existsInConfig &&
                        !collection.getName().equals(ChatService.COLLECTION_NAME) &&
                        !collection.getName().equals("all")) {
                    chunkCollectionRepository.delete(collection);
                    vectorStore.removeCollection(collection.getName());
                    changed = true;
                    logger.info("collection:deleted name={}", collection.getName());
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

            ChunkCollection allCollection = chunkCollectionRepository.findByName("all").orElse(null);
            if (allCollection == null) {
                chunkCollectionRepository.save(new ChunkCollection("all", allTrackRoots));
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
}
