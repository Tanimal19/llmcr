package com.llmcr.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "chunk_collection", uniqueConstraints = {
        @UniqueConstraint(columnNames = "chunk_collection_name")
})
public class ChunkCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_collection_id", nullable = false)
    private Long chunkCollectionId;

    @Column(name = "chunk_collection_name", columnDefinition = "TEXT", nullable = false, unique = true)
    private String chunkCollectionName;

    @ManyToMany
    @JoinTable(name = "chunk_index", joinColumns = @JoinColumn(name = "chunk_collection_id"), inverseJoinColumns = @JoinColumn(name = "chunk_id"))
    private List<Chunk> chunks = new ArrayList<>();

    public Long getChunkCollectionId() {
        return chunkCollectionId;
    }

    public void setChunkCollectionId(Long chunkCollectionId) {
        this.chunkCollectionId = chunkCollectionId;
    }

    public String getChunkCollectionName() {
        return chunkCollectionName;
    }

    public void setChunkCollectionName(String chunkCollectionName) {
        this.chunkCollectionName = chunkCollectionName;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }

    public void setChunks(List<Chunk> chunks) {
        List<Chunk> currentChunks = new ArrayList<>(this.chunks);
        for (Chunk chunk : currentChunks) {
            removeChunk(chunk);
        }

        if (chunks == null) {
            return;
        }

        for (Chunk chunk : chunks) {
            addChunk(chunk);
        }
    }

    public void addChunk(Chunk chunk) {
        if (chunk == null || chunks.contains(chunk)) {
            return;
        }
        chunks.add(chunk);
        if (!chunk.getChunkCollections().contains(this)) {
            chunk.getChunkCollections().add(this);
        }
    }

    public void removeChunk(Chunk chunk) {
        if (chunk == null || !chunks.remove(chunk)) {
            return;
        }
        chunk.getChunkCollections().remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ChunkCollection))
            return false;
        ChunkCollection other = (ChunkCollection) o;
        return chunkCollectionId != null && chunkCollectionId.equals(other.chunkCollectionId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}