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
@Table(name = "vector_store", uniqueConstraints = {
        @UniqueConstraint(columnNames = "vector_store_name")
})
public class VectorStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vector_store_id", nullable = false)
    private Long vectorStoreId;

    @Column(name = "vector_store_name", columnDefinition = "TEXT", nullable = false, unique = true)
    private String vectorStoreName;

    @ManyToMany
    @JoinTable(name = "`index`", joinColumns = @JoinColumn(name = "vector_store_id"), inverseJoinColumns = @JoinColumn(name = "chunk_id"))
    private List<Chunk> chunks = new ArrayList<>();

    public Long getVectorStoreId() {
        return vectorStoreId;
    }

    public void setVectorStoreId(Long vectorStoreId) {
        this.vectorStoreId = vectorStoreId;
    }

    public String getVectorStoreName() {
        return vectorStoreName;
    }

    public void setVectorStoreName(String vectorStoreName) {
        this.vectorStoreName = vectorStoreName;
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
        if (!chunk.getVectorStores().contains(this)) {
            chunk.getVectorStores().add(this);
        }
    }

    public void removeChunk(Chunk chunk) {
        if (chunk == null || !chunks.remove(chunk)) {
            return;
        }
        chunk.getVectorStores().remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof VectorStore))
            return false;
        VectorStore other = (VectorStore) o;
        return vectorStoreId != null && vectorStoreId.equals(other.vectorStoreId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}