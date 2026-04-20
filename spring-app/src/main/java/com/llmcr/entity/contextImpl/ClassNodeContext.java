package com.llmcr.entity.contextImpl;

import com.llmcr.entity.Context;
import com.llmcr.entity.Source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "class_node")
public class ClassNodeContext extends Context {

    @Column(name = "class_signature", nullable = false, length = 512)
    private String classSignature;

    @Column(name = "functional_enrich", columnDefinition = "LONGTEXT")
    private String functionalEnrich;

    @Column(name = "relationship_enrich", columnDefinition = "LONGTEXT")
    private String relationshipEnrich;

    @Column(name = "usage_enrich", columnDefinition = "LONGTEXT")
    private String usageEnrich;

    public ClassNodeContext() {
    }

    public ClassNodeContext(Source source, String className, String classSignature, String classCode) {
        this.setSource(source);
        this.setContextName("Class::" + className);
        this.setContent(classCode);
        this.setClassSignature(classSignature);
    }

    public String getClassSignature() {
        return classSignature;
    }

    public void setClassSignature(String classSignature) {
        this.classSignature = classSignature;
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
