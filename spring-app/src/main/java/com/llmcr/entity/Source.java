package com.llmcr.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "source", uniqueConstraints = {
        @UniqueConstraint(columnNames = "source_path")
})
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "source_path", columnDefinition = "TEXT", nullable = false, unique = true)
    private String sourcePath;

    @Column(name = "content_hash", columnDefinition = "TEXT", nullable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType = SourceType.UNDEFINED;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @OneToMany(mappedBy = "source")
    private List<Context> contexts = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_root_id")
    private TrackRoot parentTrackRoot;

    public enum SourceType {
        JAVACODE,
        PDF,
        MARKDOWN,
        ASCIIDOC,
        XML,
        UNDEFINED
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getSourceName() {
        if (sourcePath == null || sourcePath.isEmpty()) {
            return "unknown_source";
        }
        String[] parts = sourcePath.replace("\\", "/").split("/");
        return parts[parts.length - 1];
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(LocalDateTime lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public List<Context> getContexts() {
        return contexts;
    }

    public void setContexts(List<Context> contexts) {
        this.contexts = contexts;
    }

    public TrackRoot getParentTrackRoot() {
        return parentTrackRoot;
    }

    public void setParentTrackRoot(TrackRoot parentTrackRoot) {
        this.parentTrackRoot = parentTrackRoot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Source))
            return false;
        Source other = (Source) o;
        return sourceId != null && sourceId.equals(other.sourceId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}