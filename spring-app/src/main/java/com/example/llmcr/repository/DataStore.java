package com.example.llmcr.repository;

import com.example.llmcr.entity.Chunk;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.entity.IndexFile;

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
    private final ChunkRepository chunkRepo;
    private final IndexFileRepository indexFileRepo;

    @Autowired
    public DataStore(ClassNodeRepository nodeRepo, DocumentParagraphRepository documentRepo,
            ChunkRepository chunkRepo, IndexFileRepository indexFileRepo) {
        this.nodeRepo = nodeRepo;
        this.documentRepo = documentRepo;
        this.chunkRepo = chunkRepo;
        this.indexFileRepo = indexFileRepo;
    }

    // ClassNode operations
    public void save(ClassNode classNode) {
        nodeRepo.save(classNode);
    }

    public void saveAllClassNodes(List<ClassNode> classNodes) {
        nodeRepo.saveAll(classNodes);
    }

    public List<ClassNode> findAllClassNodes() {
        return nodeRepo.findAll();
    }

    public List<ClassNode> findUnprocessedClassNodes() {
        return nodeRepo.findByProcessedFalse();
    }

    public List<ClassNode> findProcessedClassNodes() {
        return nodeRepo.findByProcessedTrue();
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

    // Chunk operations
    public void save(Chunk chunk) {
        chunkRepo.save(chunk);
    }

    public void saveAllChunks(List<Chunk> chunks) {
        chunkRepo.saveAll(chunks);
    }

    public Chunk findChunkById(Long id) {
        return chunkRepo.findById(id).orElse(null);
    }

    public List<Chunk> findAllChunks() {
        return chunkRepo.findAll();
    }

    public List<Chunk> findAllChunksByIds(List<Long> ids) {
        return chunkRepo.findByIdIn(ids);
    }

    // IndexFile operations
    public IndexFile createIndexFile(IndexFile indexFile) {
        if (indexFileRepo.findByName(indexFile.getName()) == null) {
            indexFileRepo.save(indexFile);
        } else {
            System.out.println("IndexFile with name " + indexFile.getName() + " already exists. Skipping creation.");
        }
        return indexFileRepo.findByName(indexFile.getName());
    }

    // Getters for direct access if needed
    public ClassNodeRepository getNodeRepo() {
        return nodeRepo;
    }

    public DocumentParagraphRepository getDocumentRepo() {
        return documentRepo;
    }
}
