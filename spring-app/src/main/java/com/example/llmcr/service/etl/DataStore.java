package com.example.llmcr.service.etl;

import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.entity.Chunk;
import com.example.llmcr.entity.IndexSet;
import com.example.llmcr.entity.Chunk.ChunkContentType;
import com.example.llmcr.repository.ChunkRepository;
import com.example.llmcr.repository.ClassNodeRepository;
import com.example.llmcr.repository.DocumentParagraphRepository;
import com.example.llmcr.repository.IndexSetRepository;

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
    private final ChunkRepository chunkRepo;
    private final IndexSetRepository indexSetRepo;

    private static final Logger LOGGER = LoggerFactory.getLogger(DataStore.class);

    @Autowired
    public DataStore(ClassNodeRepository nodeRepo, DocumentParagraphRepository documentRepo,
            ChunkRepository chunkRepo, IndexSetRepository indexSetRepo) {
        this.nodeRepo = nodeRepo;
        this.documentRepo = documentRepo;
        this.chunkRepo = chunkRepo;
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

    // Chunk operations
    public void save(Chunk chunk) {
        chunkRepo.save(chunk);
    }

    public void saveAllChunks(List<Chunk> chunks) {
        chunkRepo.saveAll(chunks);
    }

    public void saveAllChunksByDocuments(List<Document> documents) {
        List<Chunk> chunks = documents.stream()
                .map(d -> new Chunk(d))
                .collect(Collectors.toList());
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

    public List<Chunk> findAllChunksByContentType(ChunkContentType contentType) {
        return chunkRepo.findByContentType(contentType);
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
    public void addAllChunksToIndexSetByIds(String indexName, List<Long> chunkIds) {
        IndexSet indexSet = indexSetRepo.findByName(indexName);
        if (indexSet != null) {
            List<Chunk> chunks = chunkRepo.findByIdIn(chunkIds);
            indexSet.getChunks().addAll(chunks);
            indexSetRepo.save(indexSet);

            // Also update non-owning side for bidirectional consistency
            for (Chunk chunk : chunks) {
                chunk.getIndexSets().add(indexSet);
            }
        } else {
            LOGGER.warn("Index: " + indexName + " not found. Cannot add chunks.");
        }
    }
}
