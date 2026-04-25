package com.llmcr.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

public class SmallChatClient {

    private final ChatClient chatClient;

    public SmallChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatClient getChatClient() {
        return chatClient;
    }

    public String call(String userMessage) {
        return chatClient.prompt().user(userMessage).call().content();
    }

    public ChatResponse call(Prompt prompt) {
        return chatClient.prompt(prompt).call().chatResponse();
    }
}
