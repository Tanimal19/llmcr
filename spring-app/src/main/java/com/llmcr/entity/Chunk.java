package com.llmcr.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

import org.springframework.ai.document.Document;

/**
 * content: the text used for generating vector embedding
 * source: the real source should be retrieved
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
    private ChunkContentType contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @ManyToMany(mappedBy = "chunks", fetch = FetchType.LAZY)
    private Set<IndexSet> indexSets = new HashSet<>();

    public Chunk() {
    }

    public Chunk(String content, ChunkContentType type, Source source) {
        this.content = content;
        this.contentType = type;
        this.source = source;
    }

    public Chunk(Document doc) {
        this.content = doc.getText();

        Object typeObj = doc.getMetadata().get("content_type");
        if (typeObj instanceof ChunkContentType) {
            this.contentType = (ChunkContentType) typeObj;
        } else if (typeObj instanceof String) {
            this.contentType = ChunkContentType.valueOf((String) typeObj);
        } else {
            this.contentType = ChunkContentType.UNDEFINED;
        }

        Object sourceObj = doc.getMetadata().get("source");
        if (sourceObj instanceof Source) {
            this.source = (Source) sourceObj;
        } else {
            this.source = null;
        }
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

    public ChunkContentType getContentType() {
        return contentType;
    }

    public void setContentType(ChunkContentType contentType) {
        this.contentType = contentType;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public Set<IndexSet> getIndexSets() {
        return indexSets;
    }

    public void setIndexFiles(Set<IndexSet> indexSets) {
        this.indexSets = indexSets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Chunk))
            return false;
        Chunk other = (Chunk) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public Document toDocument() {
        Document doc = new Document(this.content);
        doc.getMetadata().put("chunk_id", this.id);
        doc.getMetadata().put("content_type", this.contentType);
        doc.getMetadata().put("source", this.source);
        return doc;
    }

    public enum ChunkContentType {
        CODE,
        ENRICHMENT,
        DOCUMENT,
        UNDEFINED
    }
}
