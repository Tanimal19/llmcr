package com.example.llmcr.service.rag;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.llmcr.service.rag.augmentation.RAGTemplate;
import com.example.llmcr.service.rag.retrieval.RetrievalStrategy;
import com.example.llmcr.service.rag.retrieval.fusion.FusionStrategy;
import com.google.common.util.concurrent.RateLimiter;

public class RAGService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    private RetrievalStrategy retrievalStrategy;
    private FusionStrategy fusionStrategy;
    private RAGTemplate ragTemplate;
    private int topK;

    private RateLimiter rateLimiter = RateLimiter.create(1.0 / 90.0);

    private static final Logger LOGGER = LoggerFactory.getLogger(RAGService.class);

    public RAGService(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    public void setStrategy(RetrievalStrategy retrievalStrategy, FusionStrategy fusionStrategy) {
        this.retrievalStrategy = retrievalStrategy;
        this.fusionStrategy = fusionStrategy;
        LOGGER.info("Using retrieval strategy: "
                + retrievalStrategy.getClass().getSimpleName()
                + " and fusion strategy: "
                + fusionStrategy.getClass().getSimpleName());
    }

    public void setRAGTemplate(RAGTemplate ragTemplate) {
        this.ragTemplate = ragTemplate;
        LOGGER.info("Using RAG template: " + ragTemplate.getClass().getSimpleName());
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Map<String, Object> generation(Object input) {
        assert retrievalStrategy != null : "Retrieval Strategy is not set.";
        assert fusionStrategy != null : "Fusion Strategy is not set.";
        assert ragTemplate != null : "RAG Template is not set.";
        assert topK > 0 : "topK is not set.";

        long startTime = System.currentTimeMillis();

        // retrieve documents for each query
        List<String> queries = ragTemplate.getQueries(input);
        List<Document> documents;
        if (queries.size() == 1) {
            documents = retrievalStrategy.retrieve(queries.get(0), topK, vectorStore);
        } else {
            List<List<Document>> documentLists = queries.stream()
                    .map(query -> retrievalStrategy.retrieve(query, topK, vectorStore))
                    .toList();
            documents = fusionStrategy.fuse(documentLists, topK);
        }

        LOGGER.info("Retrieved documents:\n" + documents.stream()
                .map(d -> d.getMetadata().get("source_id").toString() + "::"
                        + d.getMetadata().get("source_name") + "::"
                        + d.getMetadata().get("similarity_score").toString())
                .reduce((s1, s2) -> s1 + "\n" + s2).orElse(""));

        long retrievalEndTime = System.currentTimeMillis();
        LOGGER.info("Retrieval completed in " + (retrievalEndTime - startTime) + "ms");

        Prompt prompt = ragTemplate.getBuilder().augmentInput(input).augmentContext(documents).build();

        // call chat model
        ChatResponse response;
        int count = 0;
        while (true) {
            rateLimiter.acquire();
            try {
                response = chatModel.call(prompt);
                break;
            } catch (Exception e) {
                LOGGER.warn("Chat model call failed: " + e.getMessage());
            }

            count++;
            LOGGER.warn("Retry: attempt #" + count);
            if (count > 5) {
                throw new RuntimeException("Failed to call chat model.");
            }
        }

        long generationEndTime = System.currentTimeMillis();
        LOGGER.info("Generation completed in " + (generationEndTime - retrievalEndTime) + "ms");

        Map<String, Object> responseBody = Map.of(
                "timestamp", java.time.Instant.now().toString(),
                "prompt", prompt.toString(),
                "documents", documents.stream().map(doc -> doc.getMetadata()).toList(), // only store metadata
                "response", response.getResult().getOutput().getText());
        return responseBody;
    }

}
