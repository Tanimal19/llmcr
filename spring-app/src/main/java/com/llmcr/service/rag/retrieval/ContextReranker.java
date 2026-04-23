package com.llmcr.service.rag.retrieval;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.service.rag.retrieval.ContextRetriever.ContextScorePair;

@Component
public class ContextReranker {
    private final EmbeddingModel embeddingModel;

    public ContextReranker(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<ContextScorePair> rerank(List<Context> context, String query) {

    }
}
