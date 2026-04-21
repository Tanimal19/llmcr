package com.llmcr.entity;

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
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "chunk")
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private Context context;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    @ManyToMany(mappedBy = "chunks")
    private List<ChunkCollection> chunkCollections = new ArrayList<>();

    public Chunk() {
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
        if (context != null && !context.getChunks().contains(this)) {
            context.getChunks().add(this);
        }
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
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
        if (chunkCollection == null || chunkCollections.contains(chunkCollection)) {
            return;
        }
        chunkCollections.add(chunkCollection);
        if (!chunkCollection.getChunks().contains(this)) {
            chunkCollection.getChunks().add(this);
        }
    }

    public void removeChunkCollection(ChunkCollection chunkCollection) {
        if (chunkCollection == null || !chunkCollections.remove(chunkCollection)) {
            return;
        }
        chunkCollection.getChunks().remove(this);
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
