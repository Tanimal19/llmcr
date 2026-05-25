package com.llmcr.entity;

import com.llmcr.util.PathUtils;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.Hibernate;

@Entity
@Table(name = "source", uniqueConstraints = { @UniqueConstraint(columnNames = "path") })
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "path", columnDefinition = "TEXT", nullable = false, unique = true)
    private String path;

    /**
     * Hash of the content of the source. Used to determine if the source has
     * changed since last sync.
     */
    @Column(name = "content_hash", columnDefinition = "TEXT", nullable = false)
    private String contentHash;

    /**
     * Determine how to extract content and split chunks from the source.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private SourceType type;

    /**
     * Whether the source has been extracted into contexts.
     */
    @Column(name = "extracted", nullable = false)
    private boolean extracted;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Context> contexts = new HashSet<>();

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

    protected Source() {}

    public Source(String path, String contentHash, SourceType type) {
        this.path = PathUtils.toRelativePath(path);
        this.contentHash = contentHash;
        this.type = type;
        this.extracted = false;
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

    public void setPath(String path) {
        this.path = path;
    }

    public String getSourceName() {
        if (path == null || path.isEmpty()) {
            return "unknown_source";
        }
        String[] parts = path.replace("\\", "/").split("/");
        return parts[parts.length - 1];
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

    public boolean isExtracted() {
        return extracted;
    }

    public void setExtracted(boolean extracted) {
        this.extracted = extracted;
    }

    public Set<Context> getContexts() {
        return contexts;
    }

    public void setContexts(Set<Context> contexts) {
        if (Hibernate.isInitialized(this.contexts)) {
            Set<Context> currentContexts = new HashSet<>(this.contexts);
            for (Context context : currentContexts) {
                removeContext(context);
            }
        }

        if (contexts == null) {
            return;
        }

        for (Context context : contexts) {
            addContext(context);
        }
    }

    public void addContext(Context context) {
        if (context == null) {
            return;
        }

        if (Hibernate.isInitialized(contexts) && !contexts.contains(context)) {
            contexts.add(context);
        }

        if (context.getSource() != this) {
            context.setSource(this);
        }
    }

    public void removeContext(Context context) {
        if (context == null) {
            return;
        }

        if (Hibernate.isInitialized(contexts)) {
            contexts.remove(context);
        }

        if (context.getSource() == this) {
            context.setSource(null);
        }
    }

    public TrackRoot getTrackRoot() {
        return trackRoot;
    }

    // package-private to prevent external call
    void setTrackRoot(TrackRoot trackRoot) {
        if (this.trackRoot == trackRoot) {
            return;
        }

        TrackRoot oldTrackRoot = this.trackRoot;
        this.trackRoot = null;
        if (oldTrackRoot != null) {
            oldTrackRoot.removeSource(this);
        }

        this.trackRoot = trackRoot;
        if (
            trackRoot != null &&
            Hibernate.isInitialized(trackRoot.getSources()) &&
            !trackRoot.getSources().contains(this)
        ) {
            trackRoot.getSources().add(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Source)) return false;
        Source other = (Source) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
