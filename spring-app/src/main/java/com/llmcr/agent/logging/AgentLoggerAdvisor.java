package com.llmcr.agent.logging;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.model.Generation;

public class AgentLoggerAdvisor implements BaseAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(AgentLoggerAdvisor.class);
    private final int order = 0;
    private final String agentName;

    public AgentLoggerAdvisor(String agentName) {
        this.agentName = agentName;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        logger.info("[{}] Input: {}", agentName, chatClientRequest.prompt().getUserMessage().getText());
        AgentContextHolder.beginIteration(chatClientRequest.prompt());
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        String responseText = extractText(chatClientResponse.chatResponse());
        if (responseText != null && !responseText.isBlank()) {
            logger.info("[{}] Output: {}", agentName, responseText);
        }

        AgentContextHolder.completeIteration(responseText != null ? responseText : chatClientResponse.chatResponse());
        return chatClientResponse;
    }

    private static String extractText(ChatResponse chatResponse) {
        return Optional.ofNullable(chatResponse)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse(null);
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public String toString() {
        return AgentLoggerAdvisor.class.getSimpleName();
    }
}
