package com.example.llmcr.service.rag;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.llmcr.service.rag.augmentation.PromptBuilder;
import com.example.llmcr.service.rag.retrieval.RetrievalStrategy;

public class RAGService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private RetrievalStrategy retrievalStrategy;
    private PromptBuilder promptBuilder;
    private int topK = 5;

    public RAGService(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    public void setStrategy(RetrievalStrategy retrievalStrategy) {
        this.retrievalStrategy = retrievalStrategy;
    }

    public void setPromptBuilder(PromptBuilder promptBuilder) {
        this.promptBuilder = promptBuilder;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Map<String, Object> generation(Object input) {
        promptBuilder = promptBuilder.augmentInput(input);
        List<Document> documents = retrievalStrategy.retrieve(promptBuilder.getQuery(), topK, vectorStore);
        Prompt prompt = promptBuilder.augmentContext(documents).build();

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
                "query", promptBuilder.getQuery(),
                "context", documents,
                "response", rawResponse);
        return entry;
    }

}
