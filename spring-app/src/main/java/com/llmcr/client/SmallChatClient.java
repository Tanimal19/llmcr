package com.llmcr.client;

import org.springframework.ai.chat.client.ChatClient;

public class SmallChatClient implements ChatClientWrapper {

    private final ChatClient chatClient;

    public SmallChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatClient getChatClient() {
        return chatClient;
    }
}
