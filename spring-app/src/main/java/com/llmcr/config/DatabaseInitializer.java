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
    private final DatabaseInitProperties properties;

    public DatabaseInitializer(TrackRootRepository trackRootRepository,
            ChunkCollectionRepository chunkCollectionRepository,
            DatabaseInitProperties properties) {
        this.trackRootRepository = trackRootRepository;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.properties = properties;
    }

    public List<TrackRoot> initTrackRoots() {
        List<TrackRoot> result = new ArrayList<>();
        for (DatabaseInitProperties.TrackRootConfig config : properties.getTrackRoots()) {
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
                        DatabaseInitProperties.TrackRootConfig::getId,
                        c -> {
                            TrackRoot t = trackRootRepository.findByPath(c.getPath());
                            return t;
                        }));

        for (DatabaseInitProperties.CollectionConfig config : properties.getCollections()) {
            ChunkCollection existing = chunkCollectionRepository.findByName(config.getName()).orElse(null);
            if (existing != null) {
                continue;
            }
            Set<TrackRoot> trackRoots = config.getTrackRoots().stream()
                    .map(trackRootById::get)
                    .filter(t -> t != null)
                    .collect(Collectors.toSet());
            ChunkCollection collection = new ChunkCollection(config.getName(), trackRoots);
            chunkCollectionRepository.save(collection);
        }
    }

    public void init() {
        initTrackRoots();
        initCollections();
    }
}
