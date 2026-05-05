package com.llmcr.service.rag;

import java.util.List;

public interface RAGInput {
    /**
     * Build retrieval queries based on the input. The queries will be used by the
     * RAGAdvisor to retrieve relevant context information before the main LLM call.
     */
    List<String> buildQueries();
}
