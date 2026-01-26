package com.example.llmcr.repository;

import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.entity.Embedding;
import com.example.llmcr.entity.IndexSet;
import com.example.llmcr.entity.Embedding.EmbeddingContentType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates repositories for data persistence and retrieval.
 */
@Component
public class DataStore {

    private final ClassNodeRepository nodeRepo;
    private final DocumentParagraphRepository documentRepo;
    private final EmbeddingRepository embeddingRepo;
    private final IndexSetRepository indexSetRepo;

    private static final Logger LOGGER = LoggerFactory.getLogger(DataStore.class);

    @Autowired
    public DataStore(ClassNodeRepository nodeRepo, DocumentParagraphRepository documentRepo,
            EmbeddingRepository embeddingRepo, IndexSetRepository indexSetRepo) {
        this.nodeRepo = nodeRepo;
        this.documentRepo = documentRepo;
        this.embeddingRepo = embeddingRepo;
        this.indexSetRepo = indexSetRepo;
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

    // Embedding operations
    public void save(Embedding embedding) {
        embeddingRepo.save(embedding);
    }

    public void saveAllEmbeddings(List<Embedding> embeddings) {
        embeddingRepo.saveAll(embeddings);
    }

    public void saveAllEmbeddingsByDocuments(List<Document> documents) {
        List<Embedding> embeddings = documents.stream()
                .map(d -> new Embedding(d))
                .collect(Collectors.toList());
        embeddingRepo.saveAll(embeddings);
    }

    public Embedding findEmbeddingById(Long id) {
        return embeddingRepo.findById(id).orElse(null);
    }

    public List<Embedding> findAllEmbeddings() {
        return embeddingRepo.findAll();
    }

    public List<Embedding> findAllEmbeddingsByIds(List<Long> ids) {
        return embeddingRepo.findByIdIn(ids);
    }

    public List<Embedding> findAllEmbeddingsByContentType(EmbeddingContentType contentType) {
        return embeddingRepo.findByContentType(contentType);
    }

    // IndexSet operations
    public void createIndexIfNotExist(String indexName) {
        if (indexSetRepo.findByName(indexName) == null) {
            indexSetRepo.save(new IndexSet(indexName));
            LOGGER.info("Created new index: " + indexName);
        } else {
            LOGGER.info("Index: " + indexName + " already exists. Skipping creation.");
        }
    }

    @Transactional
    public void addAllEmbeddingsToIndexSetByIds(String indexName, List<Long> embeddingIds) {
        IndexSet indexSet = indexSetRepo.findByName(indexName);
        if (indexSet != null) {
            List<Embedding> embeddings = embeddingRepo.findByIdIn(embeddingIds);
            indexSet.getEmbeddings().addAll(embeddings);
            indexSetRepo.save(indexSet);

            // Also update non-owning side for bidirectional consistency
            for (Embedding embedding : embeddings) {
                embedding.getIndexSets().add(indexSet);
            }
        } else {
            LOGGER.warn("Index: " + indexName + " not found. Cannot add embeddings.");
        }
    }
}
