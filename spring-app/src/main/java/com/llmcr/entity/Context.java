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

    @Column(name = "context_name", columnDefinition = "TEXT", nullable = false, unique = true)
    private String contextName;

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
        this.source = source;
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
        this.outgoingRelations = outgoingRelations;
    }

    public List<ContextRelation> getIncomingRelations() {
        return incomingRelations;
    }

    public void setIncomingRelations(List<ContextRelation> incomingRelations) {
        this.incomingRelations = incomingRelations;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }

    public void setChunks(List<Chunk> chunks) {
        this.chunks = chunks;
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