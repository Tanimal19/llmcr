package com.llmcr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "class_node")
public class ClassNode extends Context {

    @Column(name = "functional_enrich", columnDefinition = "LONGTEXT")
    private String functionalEnrich;

    @Column(name = "relationship_enrich", columnDefinition = "LONGTEXT")
    private String relationshipEnrich;

    @Column(name = "usage_enrich", columnDefinition = "LONGTEXT")
    private String usageEnrich;

    public ClassNode(Source source, String classSignature, String classCode) {
        this.setSource(source);
        this.setContextName(classSignature);
        this.setContent(classCode);
    }

    public String getFunctionalEnrich() {
        return functionalEnrich;
    }

    public void setFunctionalEnrich(String functionalEnrich) {
        this.functionalEnrich = functionalEnrich;
    }

    public String getRelationshipEnrich() {
        return relationshipEnrich;
    }

    public void setRelationshipEnrich(String relationshipEnrich) {
        this.relationshipEnrich = relationshipEnrich;
    }

    public String getUsageEnrich() {
        return usageEnrich;
    }

    public void setUsageEnrich(String usageEnrich) {
        this.usageEnrich = usageEnrich;
    }
}
