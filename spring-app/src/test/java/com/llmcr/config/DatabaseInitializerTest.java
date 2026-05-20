package com.llmcr.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.TrackRootRepository;

@ExtendWith(MockitoExtension.class)
class DatabaseInitializerTest {

    @Mock
    private TrackRootRepository trackRootRepository;

    @Mock
    private ChunkCollectionRepository chunkCollectionRepository;

    @Mock
    private ApplicationProperties properties;

    @InjectMocks
    private DatabaseInitializer databaseInitializer;

    @Captor
    private ArgumentCaptor<ChunkCollection> collectionCaptor;

    @Test
    void shouldCreateConfiguredCollectionsAndAllCollection() {
        TrackRoot trackRootA = new TrackRoot("/data/a");
        TrackRoot trackRootB = new TrackRoot("/data/b");

        ApplicationProperties.TrackRootProperties rootA = new ApplicationProperties.TrackRootProperties();
        rootA.setId("a");
        rootA.setPath("/data/a");

        ApplicationProperties.TrackRootProperties rootB = new ApplicationProperties.TrackRootProperties();
        rootB.setId("b");
        rootB.setPath("/data/b");

        ApplicationProperties.CollectionProperties projectCollection = new ApplicationProperties.CollectionProperties();
        projectCollection.setTrackRoots(List.of("a"));

        Map<String, ApplicationProperties.CollectionProperties> collections = new LinkedHashMap<>();
        collections.put("project-code", projectCollection);

        when(properties.getTrackRoots()).thenReturn(List.of(rootA, rootB));
        when(properties.getCollections()).thenReturn(collections);

        when(trackRootRepository.findByPath("/data/a")).thenReturn(trackRootA);
        when(trackRootRepository.findByPath("/data/b")).thenReturn(trackRootB);
        when(trackRootRepository.findAll()).thenReturn(List.of(trackRootA, trackRootB));

        when(chunkCollectionRepository.findByName("project-code")).thenReturn(Optional.empty());
        when(chunkCollectionRepository.findByName("all")).thenReturn(Optional.empty());

        databaseInitializer.initCollections();

        verify(chunkCollectionRepository).save(collectionCaptor.capture());
        verify(chunkCollectionRepository).save(collectionCaptor.capture());

        List<ChunkCollection> savedCollections = collectionCaptor.getAllValues();
        assertEquals(2, savedCollections.size());

        ChunkCollection savedProjectCollection = savedCollections.stream()
                .filter(c -> "project-code".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, savedProjectCollection.getTrackRoots().size());
        assertTrue(savedProjectCollection.getTrackRoots().contains(trackRootA));

        ChunkCollection savedAllCollection = savedCollections.stream()
                .filter(c -> "all".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, savedAllCollection.getTrackRoots().size());
        assertTrue(savedAllCollection.getTrackRoots().contains(trackRootA));
        assertTrue(savedAllCollection.getTrackRoots().contains(trackRootB));
    }

    @Test
    void shouldUpdateExistingAllCollectionToContainAllRegisteredTrackRoots() {
        TrackRoot trackRootA = new TrackRoot("/data/a");
        TrackRoot trackRootB = new TrackRoot("/data/b");

        ApplicationProperties.TrackRootProperties rootA = new ApplicationProperties.TrackRootProperties();
        rootA.setId("a");
        rootA.setPath("/data/a");

        ApplicationProperties.TrackRootProperties rootB = new ApplicationProperties.TrackRootProperties();
        rootB.setId("b");
        rootB.setPath("/data/b");

        ChunkCollection existingAll = new ChunkCollection("all", new HashSet<>(Set.of(trackRootA)));

        when(properties.getTrackRoots()).thenReturn(List.of(rootA, rootB));
        when(properties.getCollections()).thenReturn(Map.of());

        when(trackRootRepository.findAll()).thenReturn(List.of(trackRootA, trackRootB));
        when(chunkCollectionRepository.findByName("all")).thenReturn(Optional.of(existingAll));

        databaseInitializer.initCollections();

        verify(chunkCollectionRepository).save(existingAll);
        assertTrue(existingAll.getTrackRoots().contains(trackRootA));
        assertTrue(existingAll.getTrackRoots().contains(trackRootB));
        assertEquals(2, existingAll.getTrackRoots().size());
    }
}
