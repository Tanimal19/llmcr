package com.llmcr.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.TrackRootRepository;

@Component
public class DatabaseInitializer {

    private final TrackRootRepository trackRootRepository;
    private final ChunkCollectionRepository chunkCollectionRepository;
    private final ApplicationProperties properties;

    public DatabaseInitializer(TrackRootRepository trackRootRepository,
            ChunkCollectionRepository chunkCollectionRepository,
            ApplicationProperties properties) {
        this.trackRootRepository = trackRootRepository;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.properties = properties;

        initTrackRoots();
        initCollections();
    }

    private void initTrackRoots() {
        for (ApplicationProperties.TrackRootProperties config : properties.getTrackRoots()) {
            TrackRoot existing = trackRootRepository.findByPath(config.getPath());
            if (existing != null) {
                continue;
            }
            TrackRoot trackRoot = new TrackRoot(config.getPath(),
                    new HashSet<>(config.getAllowedSourceTypes()));
            trackRootRepository.save(trackRoot);
        }
    }

    private void initCollections() {
        Map<String, TrackRoot> trackRootById = properties.getTrackRoots().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(
                        ApplicationProperties.TrackRootProperties::getId,
                        c -> {
                            TrackRoot t = trackRootRepository.findByPath(c.getPath());
                            return t;
                        }));

        for (Map.Entry<String, ApplicationProperties.CollectionProperties> entry : properties.getCollections()
                .entrySet()) {
            String collectionName = entry.getKey();
            ApplicationProperties.CollectionProperties config = entry.getValue();

            ChunkCollection existing = chunkCollectionRepository.findByName(collectionName).orElse(null);
            if (existing != null) {
                continue;
            }
            Set<TrackRoot> trackRoots = config.getTrackRoots().stream()
                    .map(trackRootById::get)
                    .filter(t -> t != null)
                    .collect(Collectors.toSet());
            ChunkCollection collection = new ChunkCollection(collectionName, trackRoots);
            chunkCollectionRepository.save(collection);
        }
    }
}
