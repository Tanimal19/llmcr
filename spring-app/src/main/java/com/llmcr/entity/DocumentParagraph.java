package com.llmcr.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an extracted textual paragraph (chunk) from a document.
 */
@Entity
@Table(name = "document_paragraphs")
public class DocumentParagraph extends Source {

    @ManyToMany(mappedBy = "documentParagraphs", fetch = FetchType.LAZY)
    private List<ClassNode> classNodes = new ArrayList<>();

    public DocumentParagraph() {
    }

    public DocumentParagraph(String sourceName, String content) {
        this.sourceName = sourceName;
        this.content = content;
        this.processed = false;
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
