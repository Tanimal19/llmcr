package com.llmcr.service.rag.retrieval;

import java.util.List;

import com.llmcr.entity.Context;
import com.llmcr.service.vectorstore.MyVectorStore;
import com.llmcr.service.vectorstore.MyVectorStore.SearchRequest;

public interface RetrievalStrategy {
    public List<Context> retrieve(SearchRequest request, MyVectorStore vectorStore);
}
