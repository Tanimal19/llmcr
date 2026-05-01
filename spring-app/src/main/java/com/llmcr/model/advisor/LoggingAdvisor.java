package com.llmcr.model.advisor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Flux;

@Component
public class LoggingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LoggingAdvisor.class);

    private final ObjectMapper objectMapper;
    private final Path logFilePath;
    private final Object writeLock = new Object();
    private int order = Ordered.LOWEST_PRECEDENCE - 50;

    public LoggingAdvisor(ObjectMapper objectMapper,
            @Value("${llmcr.logging.advisor.file:../logs/llm-interactions.json}") String logFilePath) {
        this.objectMapper = objectMapper;
        this.logFilePath = Paths.get(logFilePath);
    }

    public LoggingAdvisor withOrder(int order) {
        this.order = order;
        return this;
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
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String promptText = extractPromptText(chatClientRequest);
        try {
            ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
            appendLog("call", promptText, extractResponseText(response), null);
            return response;
        } catch (RuntimeException ex) {
            appendLog("call", promptText, null, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        String promptText = extractPromptText(chatClientRequest);
        StringBuilder rawResponse = new StringBuilder();

        return streamAdvisorChain.nextStream(chatClientRequest)
                .doOnNext(response -> {
                    String chunk = extractResponseText(response);
                    if (chunk != null && !chunk.isBlank()) {
                        rawResponse.append(chunk);
                    }
                })
                .doOnComplete(() -> appendLog("stream", promptText, rawResponse.toString(), null))
                .doOnError(ex -> appendLog("stream", promptText, rawResponse.toString(), ex.getMessage()));
    }

    private String extractPromptText(ChatClientRequest request) {
        if (request == null || request.prompt() == null) {
            return "";
        }

        if (request.prompt().getUserMessage() != null && request.prompt().getUserMessage().getText() != null) {
            return request.prompt().getUserMessage().getText();
        }

        return request.prompt().toString();
    }

    private String extractResponseText(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return "";
        }

        String text = response.chatResponse().getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private void appendLog(String mode, String prompt, String rawResponse, String error) {
        ObjectNode logEntry = objectMapper.createObjectNode();
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("mode", mode);
        logEntry.put("prompt", prompt == null ? "" : prompt);
        logEntry.put("rawResponse", rawResponse == null ? "" : rawResponse);
        if (error != null && !error.isBlank()) {
            logEntry.put("error", error);
        }

        synchronized (writeLock) {
            try {
                Files.createDirectories(logFilePath.toAbsolutePath().getParent());

                ArrayNode root;
                if (Files.exists(logFilePath) && Files.size(logFilePath) > 0) {
                    JsonNode existing = objectMapper.readTree(logFilePath.toFile());
                    root = existing instanceof ArrayNode existingArray ? existingArray : objectMapper.createArrayNode();
                } else {
                    root = objectMapper.createArrayNode();
                }

                root.add(logEntry);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(logFilePath.toFile(), root);
            } catch (IOException ex) {
                log.warn("Failed to write LLM interaction log to {}", logFilePath, ex);
            }
        }
    }

}
