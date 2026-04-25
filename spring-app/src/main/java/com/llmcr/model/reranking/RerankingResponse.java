package com.llmcr.model.reranking;

import java.util.List;

import org.springframework.ai.model.ModelResponse;
import org.springframework.ai.model.ModelResult;
import org.springframework.ai.model.ResponseMetadata;
import org.springframework.ai.model.ResultMetadata;

public class RerankingResponse implements ModelResponse<RerankingResponse.RerankingResult> {

    private final List<RerankingResult> results;

    public RerankingResponse(List<RerankingResult> results) {
        this.results = List.copyOf(results);
    }

    @Override
    public RerankingResult getResult() {
        return this.results.isEmpty() ? null : this.results.get(0);
    }

    @Override
    public List<RerankingResult> getResults() {
        return this.results;
    }

    @Override
    public ResponseMetadata getMetadata() {
        return null;
    }

    public record RankedDocument(int index, double relevanceScore, String document) {
    }

    public static class RerankingResult implements ModelResult<RankedDocument> {

        private final RankedDocument output;

        public RerankingResult(RankedDocument output) {
            this.output = output;
        }

        @Override
        public RankedDocument getOutput() {
            return this.output;
        }

        @Override
        public ResultMetadata getMetadata() {
            return null;
        }
    }
}
