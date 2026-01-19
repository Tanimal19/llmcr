package com.example.llmcr.service.rag;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.llmcr.service.rag.augmentation.RAGTemplate;
import com.example.llmcr.service.rag.retrieval.RetrievalStrategy;
import com.example.llmcr.service.rag.retrieval.fusion.FusionStrategy;

public class RAGService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private RetrievalStrategy retrievalStrategy;
    private FusionStrategy fusionStrategy;
    private RAGTemplate ragTemplate;
    private int topK = 10;

    public RAGService(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    public void setStrategy(RetrievalStrategy retrievalStrategy, FusionStrategy fusionStrategy) {
        this.retrievalStrategy = retrievalStrategy;
        this.fusionStrategy = fusionStrategy;
    }

    public void setRAGTemplate(RAGTemplate ragTemplate) {
        this.ragTemplate = ragTemplate;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Map<String, Object> generation(Object input) {
        long startTime = System.currentTimeMillis();

        // retrieve documents for each query
        List<String> queries = ragTemplate.getQueries(input);
        List<Document> documents;
        if (queries.size() == 1) {
            documents = retrievalStrategy.retrieve(queries.get(0), topK, vectorStore);
        } else {
            System.out.println("Performing multi-query retrieval for " + queries.size() + " queries.");
            documents = retrievalStrategy.retrieveAll(queries, topK, vectorStore, fusionStrategy);
        }

        Prompt prompt = ragTemplate.getBuilder().augmentInput(input).augmentContext(documents).build();

        long retrievalEndTime = System.currentTimeMillis();
        System.out.println("+ Retrieval and Augmentation completed in " + (retrievalEndTime - startTime) + "ms");
        System.out.println("Input length: " + prompt.toString().length() + " char");

        // call chat model
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        long generationEndTime = System.currentTimeMillis();
        System.out.println("+ Generation completed in " + (generationEndTime - retrievalEndTime) + "ms");

        String rawResponse = response.getResult().getOutput().getText();

        Map<String, Object> entry = Map.of(
                "timestamp", java.time.Instant.now().toString(),
                "prompt", prompt.toString(),
                "documents", documents,
                "response", rawResponse);
        return entry;
    }

}
