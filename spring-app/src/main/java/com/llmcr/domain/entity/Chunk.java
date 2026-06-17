package com.llmcr.domain.entity;

import com.llmcr.domain.entity.converter.FloatArrayStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.Hibernate;

@Entity
@Table(name = "chunk")
public class Chunk {

    /** This id is also used in FAISS index file. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** The parent context of the chunk. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private Context context;

    /** The index of the chunk in the parent context. It's not ordered. */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /** The text used to generate embedding vector. */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Cached embedding vector for the chunk content. */
    @Convert(converter = FloatArrayStringConverter.class)
    @Column(name = "embedding", columnDefinition = "TEXT")
    private float[] embedding;

    protected Chunk() {
    }

    public Chunk(String content) {
        this.content = content;
    }

    public Chunk(Context context, Integer chunkIndex, String content) {
        setContext(context);
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        if (this.context == context) {
            return;
        }

        Context oldContext = this.context;
        this.context = null;
        if (oldContext != null) {
            oldContext.removeChunk(this);
        }

        this.context = context;
        if (context != null
                && Hibernate.isInitialized(context.getChunks())
                && !context.getChunks().contains(this)) {
            context.addChunk(this);
        }
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
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
}
