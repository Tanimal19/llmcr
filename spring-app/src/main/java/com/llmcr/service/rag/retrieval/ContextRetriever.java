package com.llmcr.service.rag.retrieval;

import java.util.List;
import java.util.function.Supplier;

import com.llmcr.entity.Context;
import com.llmcr.repository.ChunkRepository;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.vectorstore.MyVectorStore;
import com.llmcr.service.vectorstore.MyVectorStore.SearchRequest;

public class ContextRetriever {
    private final MyVectorStore vectorStore;
    private final ChunkRepository chunkRepository;
    private final ContextRepository contextRepository;

    public ContextRetriever(MyVectorStore vectorStore,
            ChunkRepository chunkRepository, ContextRepository contextRepository) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.contextRepository = contextRepository;
    }

    public List<Context> retrieve(String query, int topK, String collectionName, RetrievalStrategy retrievalStrategy) {
        SearchRequest request = new SearchRequest(query, topK, collectionName);
        return retrievalStrategy.retrieve(request, vectorStore);
    }

}
