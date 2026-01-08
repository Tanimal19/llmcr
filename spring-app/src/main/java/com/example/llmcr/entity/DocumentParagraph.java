package com.example.llmcr.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an extracted textual paragraph (chunk) from a document.
 */
@Entity
@Table(name = "document_paragraphs")
public class DocumentParagraph {

    @Id
    private String id;

    @Column(name = "source")
    private String source;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "processed")
    private boolean processed;

    @ManyToMany(mappedBy = "documentParagraphs", fetch = FetchType.LAZY)
    private List<ClassNode> classNodes = new ArrayList<>();

    // Default constructor for JPA
    public DocumentParagraph() {
    }

    public DocumentParagraph(String id, String source, String content) {
        this.id = id;
        this.source = source;
        this.content = content;
        this.processed = false;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public List<ClassNode> getClassNodes() {
        return classNodes;
    }

    public void setClassNodes(List<ClassNode> classNodes) {
        this.classNodes = classNodes;
    }

    public void addClassNode(ClassNode classNode) {
        this.classNodes.add(classNode);
    }
}
