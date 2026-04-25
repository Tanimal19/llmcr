package com.llmcr.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(length = 512)
    private List<Source.SourceType> allowedSourceTypes;

    @OneToMany(mappedBy = "trackRoot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Source> sources = new ArrayList<>();

    public TrackRoot() {
    }

    public TrackRoot(String path) {
        this.path = path;
    }

    public TrackRoot(String path, List<Source.SourceType> allowedSourceTypes) {
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

    public List<Source.SourceType> getAllowedSourceTypes() {
        return allowedSourceTypes;
    }

    public void setAllowedSourceTypes(List<Source.SourceType> allowedSourceTypes) {
        this.allowedSourceTypes = allowedSourceTypes;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        List<Source> currentSources = new ArrayList<>(this.sources);
        for (Source source : currentSources) {
            removeSource(source);
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