package com.example.llmcr.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.example.llmcr.repository.DataStore;

@Service
public class RAGService {

    private final DataStore dataStore;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;

}
