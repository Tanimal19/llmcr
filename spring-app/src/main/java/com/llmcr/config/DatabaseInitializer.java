package com.llmcr.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.TrackRootRepository;

@Component
public class DatabaseInitializer {

    @Autowired
    private final TrackRootRepository trackRootRepository;

    @Autowired
    private final ChunkCollectionRepository chunkCollectionRepository;

    @Autowired
    private final ApplicationProperties properties;

    public DatabaseInitializer(TrackRootRepository trackRootRepository,
            ChunkCollectionRepository chunkCollectionRepository,
            ApplicationProperties properties) {
        this.trackRootRepository = trackRootRepository;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.properties = properties;
    }

    public List<TrackRoot> initTrackRoots() {
        List<TrackRoot> result = new ArrayList<>();
        for (ApplicationProperties.TrackRootProperties config : properties.getTrackRoots()) {
            TrackRoot existing = trackRootRepository.findByPath(config.getPath());
            if (existing != null) {
                result.add(existing);
                continue;
            }
            TrackRoot trackRoot = new TrackRoot(config.getPath(),
                    new HashSet<>(config.getAllowedSourceTypes()));
            trackRootRepository.save(trackRoot);
            result.add(trackRoot);
        }
        return result;
    }

    public void initCollections() {
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

        ensureAllCollectionContainsAllTrackRoots();
    }

    private void ensureAllCollectionContainsAllTrackRoots() {
        Set<TrackRoot> allTrackRoots = new HashSet<>(trackRootRepository.findAll());
        ChunkCollection allCollection = chunkCollectionRepository.findByName("all").orElse(null);

        if (allCollection == null) {
            ChunkCollection collection = new ChunkCollection("all", allTrackRoots);
            chunkCollectionRepository.save(collection);
            return;
        }

        boolean changed = false;
        for (TrackRoot trackRoot : allTrackRoots) {
            if (!allCollection.getTrackRoots().contains(trackRoot)) {
                allCollection.addTrackRoot(trackRoot);
                changed = true;
            }
        }

        if (changed) {
            chunkCollectionRepository.save(allCollection);
        }
    }

    public void init() {
        initTrackRoots();
        initCollections();
    }
}
