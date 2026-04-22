package com.llmcr.service.rag.retrieval;

import java.util.List;

import com.llmcr.entity.Context;
import com.llmcr.service.vectorstore.MyVectorStore;

public interface RetrievalStrategy {
    public List<Context> retrieve(String query, int topK, MyVectorStore vectorStore);
}
