package com.llmcr.service.rag.retrieval.fusion;

import java.util.List;

import org.springframework.ai.document.Document;

public interface FusionStrategy {
    // Fusion multiple lists of documents into a single list
    public List<Document> fuse(List<List<Document>> documentsLists, int topK);
}
