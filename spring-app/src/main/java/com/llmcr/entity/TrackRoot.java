package com.llmcr.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

import com.llmcr.entity.Source.SourceType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "track_root")
public class TrackRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 1024)
    private String path;

    /**
     * Allowed source types for this track root. Source others than these types will
     * be ignored when refreshing sources for this track root.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "track_root_allowed_source_types", joinColumns = @JoinColumn(name = "track_root_id"))
    @Column(name = "source_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<SourceType> allowedSourceTypes = new HashSet<>();

    /**
     * The desinated collections that the chunks from this track root should be
     * loaded into. If empty, it will be loaded into all collections.
     */
    @ManyToMany(mappedBy = "havedTrackRoots")
    private Set<ChunkCollection> inCollections = new HashSet<>();

    /**
     * The sources under this track root.
     */
    @OneToMany(mappedBy = "trackRoot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Source> sources = new ArrayList<>();

    protected TrackRoot() {
    }

    public TrackRoot(String path) {
        this.path = path;
    }

    public TrackRoot(String path, Set<SourceType> allowedSourceTypes) {
        this.path = path;
        this.allowedSourceTypes = allowedSourceTypes;
    }

    public Long getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Set<SourceType> getAllowedSourceTypes() {
        return allowedSourceTypes;
    }

    public void setAllowedSourceTypes(Set<SourceType> allowedSourceTypes) {
        this.allowedSourceTypes = allowedSourceTypes == null ? new HashSet<>() : new HashSet<>(allowedSourceTypes);
    }

    public Set<ChunkCollection> getInCollections() {
        return inCollections;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        if (Hibernate.isInitialized(this.sources)) {
            List<Source> currentSources = new ArrayList<>(this.sources);
            for (Source source : currentSources) {
                removeSource(source);
            }
        }

        if (sources == null) {
            return;
        }

        for (Source source : sources) {
            addSource(source);
        }
    }

    public void addSource(Source source) {
        if (source == null) {
            return;
        }

        if (Hibernate.isInitialized(sources) && !sources.contains(source)) {
            sources.add(source);
        }

        if (source.getTrackRoot() != this) {
            source.setTrackRoot(this);
        }
    }

    public void removeSource(Source source) {
        if (source == null) {
            return;
        }

        if (Hibernate.isInitialized(sources)) {
            sources.remove(source);
        }

        if (source.getTrackRoot() == this) {
            source.setTrackRoot(null);
        }
    }
}