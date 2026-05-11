package com.llmcr.client;

import org.springframework.ai.chat.client.ChatClient;

public class LargeChatClient implements ChatClientWrapper {

    private final ChatClient chatClient;

    public LargeChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatClient getChatClient() {
        return chatClient;
    }
}
