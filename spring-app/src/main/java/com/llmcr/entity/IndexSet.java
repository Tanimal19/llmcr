package com.llmcr.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "index_sets")
public class IndexSet {

    @Id
    @Column(length = 255)
    private String name;

    @ManyToMany
    @JoinTable(name = "index_sets_chunks", joinColumns = @JoinColumn(name = "index_set_name"), inverseJoinColumns = @JoinColumn(name = "chunk_id"))
    private Set<Chunk> chunks = new HashSet<>();

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

    public Set<Chunk> getChunks() {
        return chunks;
    }

    public void setChunks(Set<Chunk> chunks) {
        this.chunks = chunks;
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
