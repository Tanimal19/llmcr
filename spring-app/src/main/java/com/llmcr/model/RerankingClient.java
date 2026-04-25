package com.llmcr.model;

import java.util.List;

import com.llmcr.model.reranking.RerankingModel;
import com.llmcr.model.reranking.RerankingRequest;
import com.llmcr.model.reranking.RerankingResponse;

public class RerankingClient {

    private final RerankingModel rerankingModel;

    public RerankingClient(RerankingModel rerankingModel) {
        this.rerankingModel = rerankingModel;
    }

    public RerankingModel getRerankingModel() {
        return rerankingModel;
    }

    public RerankingResponse rerank(String query, List<String> documents) {
        return rerankingModel.rerank(query, documents);
    }

    public RerankingResponse rerank(RerankingRequest request) {
        return rerankingModel.rerank(request);
    }
}
