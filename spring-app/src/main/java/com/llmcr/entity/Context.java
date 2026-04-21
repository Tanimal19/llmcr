package com.llmcr.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "context", uniqueConstraints = {
        @UniqueConstraint(columnNames = "context_name")
})
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Context {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "context_id", nullable = false)
    private Long contextId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    /**
     * A human-readable name for the context, which should be unique across all
     * contexts. Used when displaying in the UI.
     */
    @Column(name = "context_name", columnDefinition = "TEXT", nullable = false, unique = true)
    private String contextName;

    /**
     * The content that actually pass to LLM.
     */
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @OneToMany(mappedBy = "sourceContext")
    private List<ContextRelation> outgoingRelations = new ArrayList<>();

    @OneToMany(mappedBy = "targetContext")
    private List<ContextRelation> incomingRelations = new ArrayList<>();

    @OneToMany(mappedBy = "context")
    private List<Chunk> chunks = new ArrayList<>();

    public Long getContextId() {
        return contextId;
    }

    public void setContextId(Long contextId) {
        this.contextId = contextId;
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

    public String getContextName() {
        return contextName;
    }

    public void setContextName(String contextName) {
        this.contextName = contextName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ContextRelation> getOutgoingRelations() {
        return outgoingRelations;
    }

    public void setOutgoingRelations(List<ContextRelation> outgoingRelations) {
        List<ContextRelation> currentRelations = new ArrayList<>(this.outgoingRelations);
        for (ContextRelation relation : currentRelations) {
            removeOutgoingRelation(relation);
        }

        if (outgoingRelations == null) {
            return;
        }

        for (ContextRelation relation : outgoingRelations) {
            addOutgoingRelation(relation);
        }
    }

    public List<ContextRelation> getIncomingRelations() {
        return incomingRelations;
    }

    public void setIncomingRelations(List<ContextRelation> incomingRelations) {
        List<ContextRelation> currentRelations = new ArrayList<>(this.incomingRelations);
        for (ContextRelation relation : currentRelations) {
            removeIncomingRelation(relation);
        }

        if (incomingRelations == null) {
            return;
        }

        for (ContextRelation relation : incomingRelations) {
            addIncomingRelation(relation);
        }
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

    public void addOutgoingRelation(ContextRelation relation) {
        if (relation == null || outgoingRelations.contains(relation)) {
            return;
        }
        outgoingRelations.add(relation);
        if (relation.getSourceContext() != this) {
            relation.setSourceContext(this);
        }
    }

    public void removeOutgoingRelation(ContextRelation relation) {
        if (relation == null || !outgoingRelations.remove(relation)) {
            return;
        }
        if (relation.getSourceContext() == this) {
            relation.setSourceContext(null);
        }
    }

    public void addIncomingRelation(ContextRelation relation) {
        if (relation == null || incomingRelations.contains(relation)) {
            return;
        }
        incomingRelations.add(relation);
        if (relation.getTargetContext() != this) {
            relation.setTargetContext(this);
        }
    }

    public void removeIncomingRelation(ContextRelation relation) {
        if (relation == null || !incomingRelations.remove(relation)) {
            return;
        }
        if (relation.getTargetContext() == this) {
            relation.setTargetContext(null);
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
        return contextId != null && contextId.equals(other.contextId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}