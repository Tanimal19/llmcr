package com.llmcr.feature.sync;

import com.llmcr.config.SystemConfig.CollectionConfig;
import com.llmcr.config.SystemConfig.TrackRootConfig;
import com.llmcr.config.provider.CollectionConfigProvider;
import com.llmcr.config.provider.TrackRootConfigProvider;
import com.llmcr.domain.entity.ChunkCollection;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.repository.ChunkCollectionRepository;
import com.llmcr.domain.repository.TrackRootRepository;
import com.llmcr.feature.chat.ChatService;
import com.llmcr.infrastructure.vectorstore.MyVectorStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CollectionConfigSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger(CollectionConfigSynchronizer.class);
    private static final String ALL_COLLECTION_NAME = "all";

    private final CollectionConfigProvider collectionConfigProvider;
    private final TrackRootConfigProvider trackRootConfigProvider;
    private final TrackRootRepository trackRootRepository;
    private final ChunkCollectionRepository chunkCollectionRepository;
    private final MyVectorStore vectorStore;

    public CollectionConfigSynchronizer(
            CollectionConfigProvider collectionConfigProvider,
            TrackRootConfigProvider trackRootConfigProvider,
            TrackRootRepository trackRootRepository,
            ChunkCollectionRepository chunkCollectionRepository,
            MyVectorStore vectorStore) {
        this.collectionConfigProvider = collectionConfigProvider;
        this.trackRootConfigProvider = trackRootConfigProvider;
        this.trackRootRepository = trackRootRepository;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.vectorStore = vectorStore;
    }

    public boolean syncCollections() {
        syncDefaultCollections();
        boolean changed = removeUnconfiguredCollections();

        Map<String, TrackRoot> trackRootsByName = loadTrackRootsByName();
        changed |= upsertConfiguredCollections(trackRootsByName);
        return changed;
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

    private boolean removeUnconfiguredCollections() {
        boolean changed = false;
        for (ChunkCollection collection : chunkCollectionRepository.findAll()) {
            boolean existsInConfig = collectionConfigProvider.getAllConfiguredCollectionNames()
                    .contains(collection.getName());
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
        for (Map.Entry<String, TrackRootConfig> entry : trackRootConfigProvider.getAllConfiguredTrackRoots()
                .entrySet()) {
            String path = entry.getValue().path();
            TrackRoot trackRoot = trackRootRepository.findByPath(path);
            if (trackRoot != null) {
                trackRootsByName.put(entry.getKey(), trackRoot);
            }
        }
        return trackRootsByName;
    }

    private boolean upsertConfiguredCollections(Map<String, TrackRoot> trackRootsByName) {
        boolean changed = false;
        for (Map.Entry<String, CollectionConfig> entry : collectionConfigProvider.getAllConfiguredCollections()
                .entrySet()) {
            String collectionName = entry.getKey();
            CollectionConfig configuredCollection = entry.getValue();
            Set<TrackRoot> targetTrackRoots = configuredCollection
                    .trackRoots()
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
