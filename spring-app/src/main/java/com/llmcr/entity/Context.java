package com.llmcr.entity;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "context", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name")
})
@Inheritance(strategy = InheritanceType.JOINED)
public class Context {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    /**
     * The index of the context in the source. This is used to distinguish different
     * contexts from the same source, and also used to sort the contexts when
     * retrieving from the database.
     */
    @Column(name = "context_index", nullable = false)
    private Integer contextIndex;

    /**
     * A human-readable name for the context, which should be unique across all
     * contexts. Used when displaying in the UI.
     */
    @Column(name = "name", columnDefinition = "TEXT", nullable = false, unique = true)
    private String name;

    /**
     * The content that actually pass to LLM.
     */
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ContextType type;

    /**
     * A list of chunk that represent the context in vector store. This is used for
     * retrieval-augmented generation (RAG).
     */
    @OneToMany(mappedBy = "context")
    private List<Chunk> chunks = new ArrayList<>();

    public enum ContextType {
        DOCUMENT,
        USECASE,
        TOOLDEF,
        GUIDELINE,
        CODE,
    }

    public Context(
            Source source, Integer contextIndex, String name, String content, ContextType type) {
        this.source = source;
        this.contextIndex = contextIndex;
        this.name = name;
        this.content = content;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        if (this.source == source) {
            return;
        }

        Source oldSource = this.source;
        this.source = null;
        if (oldSource != null) {
            oldSource.removeContext(this);
        }

        this.source = source;
        if (source != null && !source.getContexts().contains(this)) {
            source.getContexts().add(this);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String contextName) {
        this.name = contextName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
        if (chunk.getContext() != this) {
            chunk.setContext(this);
        }
    }

    public void removeChunk(Chunk chunk) {
        if (chunk == null || !chunks.remove(chunk)) {
            return;
        }
        if (chunk.getContext() == this) {
            chunk.setContext(null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Context))
            return false;
        Context other = (Context) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Adapter for Spring AI Document class.
     */
    public record ContextDocument(
            Document document,
            Context context) {
    }

    public Document toDocument() {
        Document doc = new Document.Builder()
                .text(content)
                .metadata("id", id)
                .metadata("source", source)
                .metadata("contextIndex", contextIndex)
                .metadata("name", name)
                .metadata("type", type)
                .metadata("chunks", chunks)
                .build();
        return doc;
    }
}