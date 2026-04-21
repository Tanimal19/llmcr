package com.llmcr.service.faiss;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.ChunkRepository;
import com.llmcr.repository.ContextRepository;
import com.llmcr.storage.DataStore;

@Component
public class FaissVectorStoreFactory {

    private final FaissService faissService;
    private final EmbeddingModel embeddingModel;
    private final ContextRepository contextRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkCollectionRepository chunkCollectionRepository;

    @Autowired
    public FaissVectorStoreFactory(
            FaissService faissService,
            EmbeddingModel embeddingModel,
            ContextRepository contextRepository,
            ChunkRepository chunkRepository,
            ChunkCollectionRepository chunkCollectionRepository) {
        this.faissService = faissService;
        this.embeddingModel = embeddingModel;
        this.contextRepository = contextRepository;
        this.chunkRepository = chunkRepository;
        this.chunkCollectionRepository = chunkCollectionRepository;
    }

    public FaissVectorStore create(String indexName) {
        return new FaissVectorStore(
                faissService,
                embeddingModel,
                indexName);
    }
}