package com.llmcr.model.reranking;

import java.util.List;

import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelRequest;

public class RerankingRequest implements ModelRequest<String> {

    private final String model;
    private final String query;
    private final List<String> documents;

    public RerankingRequest(String model, String query, List<String> documents) {
        this.model = model;
        this.query = query;
        this.documents = List.copyOf(documents);
    }

    @Override
    public String getInstructions() {
        return this.query;
    }

    @Override
    public ModelOptions getOptions() {
        return null;
    }

    public String getModel() {
        return this.model;
    }

    public String getQuery() {
        return this.query;
    }

    public List<String> getDocuments() {
        return this.documents;
    }

}
