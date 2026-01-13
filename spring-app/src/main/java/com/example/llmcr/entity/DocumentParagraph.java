package com.example.llmcr.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an extracted textual paragraph (chunk) from a document.
 */
@Entity
@Table(name = "document_paragraphs")
public class DocumentParagraph {

    @Id
    private UUID id;

    @Column(name = "source", columnDefinition = "TEXT", nullable = false)
    private String source;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToMany(mappedBy = "documentParagraphs", fetch = FetchType.LAZY)
    private List<ClassNode> classNodes = new ArrayList<>();

    // Default constructor for JPA
    public DocumentParagraph() {
    }

    public DocumentParagraph(UUID id, String source, String content) {
        this.id = id;
        this.source = source;
        this.content = content;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
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
