package com.llmcr.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an extracted class or code unit (e.g., for RAG indexing).
 */
@Entity
@Table(name = "class_nodes")
public class ClassNode extends Source {
    @Column(name = "signature", nullable = false)
    private String signature;

    @Column(name = "description_text", columnDefinition = "TEXT")
    private String descriptionText;

    @Column(name = "usage_text", columnDefinition = "TEXT")
    private String usageText;

    @Column(name = "relationship_text", columnDefinition = "TEXT")
    private String relationshipText;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "class_node_related_document_paragraph", joinColumns = @JoinColumn(name = "class_node_id"), inverseJoinColumns = @JoinColumn(name = "document_paragraph_id"))
    private List<DocumentParagraph> documentParagraphs = new ArrayList<>();

    public ClassNode() {
    }

    public ClassNode(String sourceName, String content, String signature) {
        this.sourceName = sourceName;
        this.content = content;
        this.signature = signature;
        this.processed = false;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public void setDescriptionText(String descriptionText) {
        this.descriptionText = descriptionText;
    }

    public String getUsageText() {
        return usageText;
    }

    public void setUsageText(String usageText) {
        this.usageText = usageText;
    }

    public String getRelationshipText() {
        return relationshipText;
    }

    public void setRelationshipText(String relationshipText) {
        this.relationshipText = relationshipText;
    }

    public List<DocumentParagraph> getDocumentParagraphs() {
        return documentParagraphs;
    }

    public void setDocumentParagraphs(List<DocumentParagraph> documentParagraphs) {
        this.documentParagraphs = documentParagraphs;
    }
}
