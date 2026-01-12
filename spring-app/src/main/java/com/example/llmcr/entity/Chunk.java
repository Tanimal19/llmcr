package com.example.llmcr.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Represents an extracted chunk used for RAG indexing.
 */
@Entity
@Table(name = "chunks")
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    public Chunk() {
    }

    public Chunk(String content, UUID sourceId) {
        this.content = content;
        this.sourceId = sourceId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", sourceId=" + sourceId +
                '}';
    }
}
