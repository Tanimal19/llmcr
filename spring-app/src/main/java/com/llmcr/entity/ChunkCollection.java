package com.llmcr.entity;

import java.util.HashSet;
import java.util.Set;

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
    @JoinTable(name = "have_chunks", joinColumns = @JoinColumn(name = "chunk_collection_id"), inverseJoinColumns = @JoinColumn(name = "chunk_id"))
    private Set<Chunk> havedChunks = new HashSet<>();

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

    public Set<Chunk> getChunks() {
        return havedChunks;
    }

    public void addChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }

        if (!havedChunks.contains(chunk)) {
            havedChunks.add(chunk);
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

        havedChunks.remove(chunk);

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