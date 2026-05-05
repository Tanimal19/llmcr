package com.llmcr.client;

import org.springframework.ai.chat.client.ChatClient;

public interface ChatClientWrapper {
    ChatClient getChatClient();
}
