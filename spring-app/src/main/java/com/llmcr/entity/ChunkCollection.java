package com.llmcr.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

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
        @UniqueConstraint(columnNames = "name")
})
public class ChunkCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", columnDefinition = "TEXT", nullable = false, unique = true)
    private String name;

    @ManyToMany
    @JoinTable(name = "chunk_index", joinColumns = @JoinColumn(name = "chunk_collection_id"), inverseJoinColumns = @JoinColumn(name = "chunk_id"))
    private List<Chunk> chunks = new ArrayList<>();

    protected ChunkCollection() {
    }

    public ChunkCollection(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String chunkCollectionName) {
        this.name = chunkCollectionName;
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
        if (chunk == null) {
            return;
        }

        if (Hibernate.isInitialized(chunks) && !chunks.contains(chunk)) {
            chunks.add(chunk);
        }

        if (Hibernate.isInitialized(chunk.getChunkCollections())
                && !chunk.getChunkCollections().contains(this)) {
            chunk.getChunkCollections().add(this);
        }
    }

    public void removeChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }

        if (Hibernate.isInitialized(chunks)) {
            chunks.remove(chunk);
        }

        if (Hibernate.isInitialized(chunk.getChunkCollections())) {
            chunk.getChunkCollections().remove(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ChunkCollection))
            return false;
        ChunkCollection other = (ChunkCollection) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}