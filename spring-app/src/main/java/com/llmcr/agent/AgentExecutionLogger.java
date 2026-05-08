package com.llmcr.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class AgentExecutionLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionLogger.class);
    private static final String OUTPUT_DIR = "../logs/reviews";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private AgentExecutionLogger() {
    }

    public static void write(AgentExecuteEntry entry) {
        try {
            Path dir = Paths.get(OUTPUT_DIR);
            Files.createDirectories(dir);

            String agent = sanitize(entry.agentName);
            String conversation = sanitize(entry.conversationId);
            String filename = String.format("agent_%s_%s_%d_%s.json",
                    agent,
                    conversation,
                    Instant.now().toEpochMilli(),
                    UUID.randomUUID().toString().substring(0, 8));

            OBJECT_MAPPER.writeValue(dir.resolve(filename).toFile(), entry);
        } catch (IOException e) {
            log.warn("Failed to write agent execution log: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Failed to serialize agent execution log: {}", e.getMessage());
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
