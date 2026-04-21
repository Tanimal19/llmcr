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
    @Column(name = "chunk_id", nullable = false)
    private Long chunkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private Context context;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    @ManyToMany(mappedBy = "chunks")
    private List<VectorStore> vectorStores = new ArrayList<>();

    public Chunk() {
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
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

    public List<VectorStore> getVectorStores() {
        return vectorStores;
    }

    public void setVectorStores(List<VectorStore> vectorStores) {
        List<VectorStore> currentVectorStores = new ArrayList<>(this.vectorStores);
        for (VectorStore vectorStore : currentVectorStores) {
            removeVectorStore(vectorStore);
        }

        if (vectorStores == null) {
            return;
        }

        for (VectorStore vectorStore : vectorStores) {
            addVectorStore(vectorStore);
        }
    }

    public void addVectorStore(VectorStore vectorStore) {
        if (vectorStore == null || vectorStores.contains(vectorStore)) {
            return;
        }
        vectorStores.add(vectorStore);
        if (!vectorStore.getChunks().contains(this)) {
            vectorStore.getChunks().add(this);
        }
    }

    public void removeVectorStore(VectorStore vectorStore) {
        if (vectorStore == null || !vectorStores.remove(vectorStore)) {
            return;
        }
        vectorStore.getChunks().remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Chunk))
            return false;
        Chunk other = (Chunk) o;
        return chunkId != null && chunkId.equals(other.chunkId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
