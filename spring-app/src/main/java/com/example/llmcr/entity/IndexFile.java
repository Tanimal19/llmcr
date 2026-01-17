package com.example.llmcr.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "index_files")
public class IndexFile {

    @Id
    @Column(length = 255)
    private String name;

    @ManyToMany(mappedBy = "indexFiles")
    private Set<Chunk> chunks = new HashSet<>();

    public IndexFile() {
    }

    public IndexFile(String name) {
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
}
