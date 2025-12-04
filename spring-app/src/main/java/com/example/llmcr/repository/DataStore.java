package com.example.llmcr.repository;

import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aggregates repositories for data persistence and retrieval.
 */
@Component
public class DataStore {

    private final ClassNodeRepository nodeRepo;
    private final DocumentParagraphRepository documentRepo;

    @Autowired
    public DataStore(ClassNodeRepository nodeRepo, DocumentParagraphRepository documentRepo) {
        this.nodeRepo = nodeRepo;
        this.documentRepo = documentRepo;
    }

    // ClassNode operations
    public void save(ClassNode classNode) {
        nodeRepo.save(classNode);
    }

    public void saveAllClassNodes(List<ClassNode> classNodes) {
        nodeRepo.saveAll(classNodes);
    }

    public List<ClassNode> findAll(List<String> ids) {
        return nodeRepo.findByIdIn(ids);
    }

    public List<ClassNode> findAllClassNodes() {
        return nodeRepo.findAll();
    }

    public List<ClassNode> findUnprocessedClassNodes() {
        return nodeRepo.findByProcessedFalse();
    }

    // DocumentParagraph operations
    public void save(DocumentParagraph paragraph) {
        documentRepo.save(paragraph);
    }

    public void saveAllDocumentParagraphs(List<DocumentParagraph> paragraphs) {
        documentRepo.saveAll(paragraphs);
    }

    public List<DocumentParagraph> findAllDocumentParagraphsByKeyword(String keyword) {
        return documentRepo.findByContentContainingIgnoreCase(keyword);
    }

    public List<DocumentParagraph> findAllDocumentParagraphs() {
        return documentRepo.findAll();
    }

    public List<DocumentParagraph> findUnprocessedDocumentParagraphs() {
        return documentRepo.findByProcessedFalse();
    }

    // Getters for direct access if needed
    public ClassNodeRepository getNodeRepo() {
        return nodeRepo;
    }

    public DocumentParagraphRepository getDocumentRepo() {
        return documentRepo;
    }
}
