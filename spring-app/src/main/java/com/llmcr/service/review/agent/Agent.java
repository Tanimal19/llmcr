package com.llmcr.service.review.agent;

import org.springframework.ai.chat.client.ChatClient;

public abstract class Agent<I, O> {

    public O execute(I input) {
        return parseOutput(invoke(parseInput(input)));
    }

    protected final ChatClient.CallResponseSpec invoke(String userMessage) {
        return invoke(systemPrompt(), userMessage);
    }

    protected final ChatClient.CallResponseSpec invoke(String systemPrompt, String userMessage) {
        return chatClient()
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call();
    }

    protected abstract ChatClient chatClient();

    protected abstract String systemPrompt();

    protected String parseInput(I input) {
        throw new UnsupportedOperationException("parseInput must be implemented or execute overridden");
    }

    protected O parseOutput(ChatClient.CallResponseSpec response) {
        throw new UnsupportedOperationException("parseOutput must be implemented or execute overridden");
    }
}
