package com.llmcr.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "chunk")
public class Chunk {

    /**
     * This id is also used in FAISS index file.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * The parent context of the chunk.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private Context context;

    /**
     * The index of the chunk in the parent context. It's not ordered.
     */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /**
     * The text used to generate embedding vector.
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * Which collections the chunk belongs to. A chunk can belong to multiple
     * collections, and a collection can have multiple chunks. The collection is
     * determined by the context type, but it's not strictly one-to-one.
     * The chunk will only be add to a collection after loading, so that we can
     * track which chunks have been loaded.
     */
    @ManyToMany(mappedBy = "chunks")
    private List<ChunkCollection> chunkCollections = new ArrayList<>();

    protected Chunk() {
    }

    public Chunk(String content) {
        this.content = content;
    }

    public Chunk(Context context, Integer chunkIndex, String content) {
        setContext(context);
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        if (this.context == context) {
            return;
        }

        Context oldContext = this.context;
        this.context = null;
        if (oldContext != null) {
            oldContext.removeChunk(this);
        }

        this.context = context;
        if (context != null
                && !context.getChunks().contains(this)) {
            context.addChunk(this);
        }
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ChunkCollection> getChunkCollections() {
        return chunkCollections;
    }

    public void setChunkCollections(List<ChunkCollection> chunkCollections) {
        List<ChunkCollection> currentChunkCollections = new ArrayList<>(this.chunkCollections);
        for (ChunkCollection chunkCollection : currentChunkCollections) {
            removeChunkCollection(chunkCollection);
        }

        if (chunkCollections == null) {
            return;
        }

        for (ChunkCollection chunkCollection : chunkCollections) {
            addChunkCollection(chunkCollection);
        }
    }

    public void addChunkCollection(ChunkCollection chunkCollection) {
        if (chunkCollection == null) {
            return;
        }

        if (Hibernate.isInitialized(chunkCollections) && !chunkCollections.contains(chunkCollection)) {
            chunkCollections.add(chunkCollection);
        }

        if (Hibernate.isInitialized(chunkCollection.getChunks())
                && !chunkCollection.getChunks().contains(this)) {
            chunkCollection.getChunks().add(this);
        }
    }

    public void removeChunkCollection(ChunkCollection chunkCollection) {
        if (chunkCollection == null) {
            return;
        }

        if (Hibernate.isInitialized(chunkCollections)) {
            chunkCollections.remove(chunkCollection);
        }

        if (Hibernate.isInitialized(chunkCollection.getChunks())) {
            chunkCollection.getChunks().remove(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Chunk))
            return false;
        Chunk other = (Chunk) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
