package com.example.llmcr.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Represents a set of embeddings grouped under a specific index name in FAISS.
 */
@Entity
@Table(name = "index_sets")
public class IndexSet {

    @Id
    @Column(length = 255)
    private String name;

    @ManyToMany
    @JoinTable(name = "index_sets_embeddings", joinColumns = @JoinColumn(name = "index_set_name"), inverseJoinColumns = @JoinColumn(name = "chunk_id"))
    private Set<Embedding> embeddings = new HashSet<>();

    public IndexSet() {
    }

    public IndexSet(String name) {
        this.name = name;
    }

    // getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Embedding> getEmbeddings() {
        return embeddings;
    }

    public void setChunks(Set<Embedding> embeddings) {
        this.embeddings = embeddings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof IndexSet))
            return false;
        return name != null && name.equals(((IndexSet) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
