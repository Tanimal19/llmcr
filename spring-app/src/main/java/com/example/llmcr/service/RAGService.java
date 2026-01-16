package com.example.llmcr.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.llmcr.service.rag.strategy.RAGStrategy;
import com.example.llmcr.utils.JsonBackupUtils;

@Service
public class RAGService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private RAGStrategy ragStrategy;

    @Value("${rag.options.topk:5}")
    private int topK;

    @Value("${rag.backup.transformChatHistoryFile:rag_history.json}")
    private String chatHistoryFile;

    private final PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template(
                    """
                            <query>

                            Context information is below.

                            ---------------------
                            <question_answer_context>
                            ---------------------

                            Follow these rules:
                            1. If the answer is not in the context, just say that you don't know.
                            2. Avoid statements like "Based on the context..." or "The provided information...".
                            """)
            .build();

    @Autowired
    public RAGService(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    public void setStrategy(RAGStrategy strategy) {
        this.ragStrategy = strategy;
        this.ragStrategy.setVectorStore(vectorStore);
    }

    public String answerQuery(String query) {
        List<Document> documents = ragStrategy.retrieveRelevantChunks(query, 5);

        // build prompt
        StringBuilder contextBuilder = new StringBuilder();
        for (Document doc : documents) {
            contextBuilder.append(doc.getText()).append("\n");
        }
        Map<String, Object> variables = Map.of(
                "query", query,
                "question_answer_context", contextBuilder.toString().trim());
        Prompt prompt = promptTemplate.create(variables);

        // call chat model
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating answer.";
        }

        String rawResponse = response.getResult().getOutput().getText();

        // backup history into json
        try {
            Map<String, Object> entry = Map.of(
                    "timestamp", java.time.Instant.now().toString(),
                    "query", query,
                    "relevant_chunks", documents,
                    "response", rawResponse);
            JsonBackupUtils.appendJsonBackup(chatHistoryFile, entry);
        } catch (IOException e) {
            System.err.println("Failed to save history: " + e.getMessage());
        }

        return rawResponse;
    }

}
