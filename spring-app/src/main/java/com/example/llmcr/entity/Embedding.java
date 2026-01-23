package com.example.llmcr.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

import org.springframework.ai.document.Document;

/**
 * Represents an embedding in a (FAISS) IndexSet.
 * content: the text used for generating vector embedding
 * source: the real source should be retrieved
 * index_sets: the index sets this embedding belongs to
 */
@Entity
@Table(name = "embeddings")
public class Embedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private EmbeddingContentType contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @ManyToMany(mappedBy = "embeddings", fetch = FetchType.LAZY)
    private Set<IndexSet> indexSets = new HashSet<>();

    public Embedding() {
    }

    public Embedding(String content, EmbeddingContentType type, Source source) {
        this.content = content;
        this.contentType = type;
        this.source = source;
    }

    public Embedding(Document doc) {
        this.content = doc.getText();

        Object typeObj = doc.getMetadata().get("content_type");
        if (typeObj instanceof EmbeddingContentType) {
            this.contentType = (EmbeddingContentType) typeObj;
        } else if (typeObj instanceof String) {
            this.contentType = EmbeddingContentType.valueOf((String) typeObj);
        } else {
            this.contentType = EmbeddingContentType.UNDEFINED;
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

    public EmbeddingContentType getContentType() {
        return contentType;
    }

    public void setContentType(EmbeddingContentType contentType) {
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
        if (!(o instanceof Embedding))
            return false;
        Embedding other = (Embedding) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public Document toDocument() {
        Document doc = new Document(this.content);
        doc.getMetadata().put("embedding_id", this.id);
        doc.getMetadata().put("content_type", this.contentType);
        doc.getMetadata().put("source", this.source);
        return doc;
    }

    public enum EmbeddingContentType {
        CODE,
        ENRICHMENT,
        DOCUMENT,
        UNDEFINED
    }
}
