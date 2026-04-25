package com.llmcr.model.reranking;

import java.util.List;

import org.springframework.ai.model.Model;
import org.springframework.util.Assert;

public interface RerankingModel extends Model<RerankingRequest, RerankingResponse> {

    RerankingResponse rerank(RerankingRequest request);

    RerankingResponse rerank(String query, List<String> documents);

    default RerankingResponse rerank(String model, String query, List<String> documents) {
        Assert.hasText(model, "model must not be blank");
        Assert.hasText(query, "query must not be blank");
        Assert.notEmpty(documents, "documents must not be empty");
        return rerank(new RerankingRequest(model, query, documents));
    }

    @Override
    default RerankingResponse call(RerankingRequest request) {
        return rerank(request);
    }
}
