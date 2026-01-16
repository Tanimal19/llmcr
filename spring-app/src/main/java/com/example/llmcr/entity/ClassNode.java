package com.example.llmcr.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an extracted class or code unit (e.g., for RAG indexing).
 */
@Entity
@Table(name = "class_nodes")
public class ClassNode {

    @Id
    private UUID id;

    @Column(name = "signature", columnDefinition = "TEXT", nullable = false)
    private String signature;

    @Column(name = "code_text", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String codeText;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "class_node_document_paragraph", joinColumns = @JoinColumn(name = "class_node_id"), inverseJoinColumns = @JoinColumn(name = "document_paragraph_id"))
    private List<DocumentParagraph> documentParagraphs = new ArrayList<>();

    @Column(name = "description_text", columnDefinition = "TEXT")
    private String descriptionText;

    @Column(name = "usage_text", columnDefinition = "TEXT")
    private String usageText;

    @Column(name = "relationship_text", columnDefinition = "TEXT")
    private String relationshipText;

    @Column(name = "processed")
    private boolean processed;

    // Default constructor for JPA
    public ClassNode() {
    }

    public ClassNode(UUID id, String signature, String codeText) {
        this.id = id;
        this.signature = signature;
        this.codeText = codeText;
        this.processed = false;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getCodeText() {
        return codeText;
    }

    public void setCodeText(String codeText) {
        this.codeText = codeText;
    }

    public List<DocumentParagraph> getDocumentParagraphs() {
        return documentParagraphs;
    }

    public void setDocumentParagraphs(List<DocumentParagraph> documentParagraphs) {
        this.documentParagraphs = documentParagraphs;
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

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public void addDocumentParagraph(DocumentParagraph paragraph) {
        this.documentParagraphs.add(paragraph);
    }
}
