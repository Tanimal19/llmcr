package com.llmcr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "context_relation")
public class ContextRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_id", nullable = false)
    private Long relationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_context_id", nullable = false)
    private Context sourceContext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_context_id", nullable = false)
    private Context targetContext;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    private RelationType relationType = RelationType.UNDEFINED;

    public enum RelationType {
        RELATED,
        MENTION,
        UNDEFINED
    }

    public Long getRelationId() {
        return relationId;
    }

    public void setRelationId(Long relationId) {
        this.relationId = relationId;
    }

    public Context getSourceContext() {
        return sourceContext;
    }

    public void setSourceContext(Context sourceContext) {
        this.sourceContext = sourceContext;
    }

    public Context getTargetContext() {
        return targetContext;
    }

    public void setTargetContext(Context targetContext) {
        this.targetContext = targetContext;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(RelationType relationType) {
        this.relationType = relationType;
    }
}