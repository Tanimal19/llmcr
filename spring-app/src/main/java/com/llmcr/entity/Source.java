package com.llmcr.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
        @UniqueConstraint(columnNames = "path")
})
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "path", columnDefinition = "TEXT", nullable = false, unique = true)
    private String path;

    @Column(name = "content_hash", columnDefinition = "TEXT", nullable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private SourceType type;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Context> contexts = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_root_id")
    private TrackRoot trackRoot;

    public enum SourceType {
        JAVACODE,
        PDF,
        MARKDOWN,
        ASCIIDOC,
        JSON,
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public String getSourceName() {
        if (path == null || path.isEmpty()) {
            return "unknown_source";
        }
        String[] parts = path.replace("\\", "/").split("/");
        return parts[parts.length - 1];
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public SourceType getType() {
        return type;
    }

    public void setType(SourceType type) {
        this.type = type;
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
        List<Context> currentContexts = new ArrayList<>(this.contexts);
        for (Context context : currentContexts) {
            removeContext(context);
        }

        if (contexts == null) {
            return;
        }

        for (Context context : contexts) {
            addContext(context);
        }
    }

    public TrackRoot getTrackRoot() {
        return trackRoot;
    }

    public void setTrackRoot(TrackRoot trackRoot) {
        if (this.trackRoot == trackRoot) {
            return;
        }

        TrackRoot oldTrackRoot = this.trackRoot;
        this.trackRoot = null;
        if (oldTrackRoot != null) {
            oldTrackRoot.removeSource(this);
        }

        this.trackRoot = trackRoot;
        if (trackRoot != null && !trackRoot.getSources().contains(this)) {
            trackRoot.getSources().add(this);
        }
    }

    public void addContext(Context context) {
        if (context == null || contexts.contains(context)) {
            return;
        }
        contexts.add(context);
        if (context.getSource() != this) {
            context.setSource(this);
        }
    }

    public void removeContext(Context context) {
        if (context == null || !contexts.remove(context)) {
            return;
        }
        if (context.getSource() == this) {
            context.setSource(null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Source))
            return false;
        Source other = (Source) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}