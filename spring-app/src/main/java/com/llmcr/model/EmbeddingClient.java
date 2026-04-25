package com.llmcr.model;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

public class EmbeddingClient {

    private final EmbeddingModel embeddingModel;

    public EmbeddingClient(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    public EmbeddingResponse embedForResponse(List<String> texts) {
        return embeddingModel.embedForResponse(texts);
    }
}
