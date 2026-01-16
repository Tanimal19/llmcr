package com.example.llmcr.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.llmcr.service.rag.strategy.RAGStrategy;

@Service
public class RAGService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private RAGStrategy ragStrategy;

    @Value("${rag.options.topk:5}")
    private int topK;

    @Autowired
    public RAGService(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    public void setStrategy(RAGStrategy strategy) {
        this.ragStrategy = strategy;
        this.ragStrategy.setVectorStore(vectorStore);
    }

    public Map<String, Object> generation(String query, PromptTemplate promptTemplate, Map<String, Object> variables) {
        List<Document> documents = ragStrategy.retrieveRelevantChunks(query, 5);

        // build prompt
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            contextBuilder.append((i + 1)).append(". ").append(documents.get(i).getText()).append("\n");
        }
        variables.put("context", contextBuilder.toString());
        Prompt prompt = promptTemplate.create(variables);

        // call chat model
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        String rawResponse = response.getResult().getOutput().getText();

        Map<String, Object> entry = Map.of(
                "timestamp", java.time.Instant.now().toString(),
                "full_prompt", prompt.toString(),
                "query", query,
                "context", documents,
                "response", rawResponse);
        return entry;
    }

}
