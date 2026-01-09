package com.example.llmcr.repository;

import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    public List<DocumentParagraph> findAllDocumentParagraphsByKeywords(List<String> keywords, int limit) {
        List<DocumentParagraph> unionList = new ArrayList<>();
        List<DocumentParagraph> intersectionList = new ArrayList<>();

        // Pre-lowercase keywords once for performance
        List<String> lowerKeywords = keywords.stream()
                .map(String::toLowerCase)
                .toList();

        for (DocumentParagraph paragraph : documentRepo.findAll()) {
            String contentLower = paragraph.getContent().toLowerCase();

            // Check matchesAll first since it's a subset condition
            boolean matchesAll = lowerKeywords.stream()
                    .allMatch(contentLower::contains);

            if (matchesAll) {
                // If matches all, it also matches any
                intersectionList.add(paragraph);
                unionList.add(paragraph);
            } else {
                // Only check matchesAny if matchesAll is false
                boolean matchesAny = lowerKeywords.stream()
                        .anyMatch(contentLower::contains);
                if (matchesAny) {
                    unionList.add(paragraph);
                }
            }

            // Early exit: if we have enough intersection results and union is already too
            // large
            if (intersectionList.size() > limit && unionList.size() > limit) {
                break;
            }
        }

        if (unionList.size() <= limit) {
            return unionList;
        }
        if (intersectionList.size() >= limit) {
            return intersectionList.subList(0, limit);
        }

        // if intersection results <= limit, return all intersection +
        // enough union to fill the limit
        List<DocumentParagraph> result = new ArrayList<>(intersectionList);
        result.addAll(unionList.subList(0, limit - intersectionList.size()));
        return result;
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
