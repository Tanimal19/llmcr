package com.llmcr.service.review.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;

public interface AgentInvokeStrategy<I, O> {

    ChatClient chatClient();

    String systemPrompt();

    String parseInput(I input);

    O parseOutput(ChatClient.CallResponseSpec response);

    default Object[] tools(I input) {
        return new Object[0];
    }

    default Integer ragTopK() {
        return null;
    }

    default String ragCollectionName() {
        return null;
    }

    default List<String> ragQueries(I input, String userMessage) {
        return userMessage == null || userMessage.isBlank() ? List.of() : List.of(userMessage);
    }
}