package com.example.llmcr.entity;

import jakarta.persistence.*;
import java.util.UUID;

import org.springframework.ai.document.Document;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ChunkType type;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    public Chunk() {
    }

    public Chunk(String content, UUID sourceId, ChunkType type) {
        this.content = content;
        this.sourceId = sourceId;
        this.type = type;
    }

    public Chunk(Document doc) {
        this.content = doc.getText();
        this.sourceId = (UUID) doc.getMetadata().get("source_id");
        String typeString = (String) doc.getMetadata().get("chunk_type");
        this.type = (typeString != null && !typeString.isEmpty())
                ? ChunkType.valueOf(typeString)
                : ChunkType.UNDEFINED;
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

    public ChunkType getType() {
        return type;
    }

    public void setType(ChunkType type) {
        this.type = type;
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

    public Document toDocument() {
        Document doc = new Document(this.content);
        doc.getMetadata().put("chunk_id", this.id.toString());
        doc.getMetadata().put("chunk_type", this.type.toString());
        doc.getMetadata().put("source_id", this.sourceId.toString());
        return doc;
    }

    public enum ChunkType {
        CODE,
        SUMMARY,
        PARAGRAPH,
        UNDEFINED
    }
}
