package com.example.llmcr.service.rag.strategy;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class BaseRAGStrategy {

    private final VectorStore vectorStore;

    private final PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template(
                    """
                            You are a knowledgeable java engineer and code reviewer. Answer the following question using the provided context information.

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

    public BaseRAGStrategy(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> retrieveRelevantChunks(String query, int topK) {
        // Default implementation (can be overridden by subclasses)
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        return vectorStore.similaritySearch(request);
    }

    public Prompt augmentPrompt(String query, List<Document> documents) {
        // Default implementation (can be overridden by subclasses)
        StringBuilder contextBuilder = new StringBuilder();
        for (Document doc : documents) {
            contextBuilder.append(doc.getText()).append("\n");
        }

        Map<String, Object> variables = Map.of(
                "query", query,
                "question_answer_context", contextBuilder.toString().trim());

        return promptTemplate.create(variables);
    }

}
