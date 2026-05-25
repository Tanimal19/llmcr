package com.llmcr.runner;

import com.llmcr.service.ChatService;
import com.llmcr.service.ChatService.ChatResponse;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "chat")
public class ChatRunner implements CommandLineRunner {

    private final ChatService chatService;

    public ChatRunner(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ChatResponse response = chatService.chat("What is VectorStore?");
        System.out.println(response);
    }
}
