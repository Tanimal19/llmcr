package com.example.llmcr.service.rag.augmentation;

import java.util.List;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

public interface PromptBuilder {
    public PromptBuilder augmentInput(Object input);

    public PromptBuilder augmentContext(List<Document> documents);

    public Prompt build();

    public String getQuery();
}
