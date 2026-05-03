package com.llmcr.service.review.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class AgentStepLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentStepLogger.class);

    private final ObjectMapper objectMapper;
    private final Path logFilePath;
    private final Object writeLock = new Object();

    public AgentStepLogger(ObjectMapper objectMapper,
            @Value("${llmcr.logging.review.history.file:../logs/agent-steps.json}") String logFilePath) {
        this.objectMapper = objectMapper;
        this.logFilePath = Paths.get(logFilePath);
    }

    public void logSuccess(String agentName, Object input, String parsedInput, Object output) {
        appendLog(agentName, "success", input, parsedInput, output, null);
    }

    public void logFailure(String agentName, Object input, String parsedInput, Throwable error) {
        appendLog(agentName, "failure", input, parsedInput, null, error);
    }

    private void appendLog(String agentName, String status, Object input, String parsedInput, Object output,
            Throwable error) {
        ObjectNode logEntry = objectMapper.createObjectNode();
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("agent", agentName == null ? "" : agentName);
        logEntry.put("status", status);
        logEntry.set("input", toJsonNode(input));
        logEntry.put("parsedInput", parsedInput == null ? "" : parsedInput);
        logEntry.set("output", toJsonNode(output));
        if (error != null) {
            logEntry.put("error", error.getMessage() == null ? error.toString() : error.getMessage());
        }

        synchronized (writeLock) {
            try {
                Path absoluteLogFilePath = logFilePath.toAbsolutePath();
                Path parent = absoluteLogFilePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                ArrayNode root;
                if (Files.exists(absoluteLogFilePath) && Files.size(absoluteLogFilePath) > 0) {
                    JsonNode existing = objectMapper.readTree(absoluteLogFilePath.toFile());
                    root = existing instanceof ArrayNode existingArray ? existingArray : objectMapper.createArrayNode();
                } else {
                    root = objectMapper.createArrayNode();
                }

                root.add(logEntry);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(absoluteLogFilePath.toFile(), root);
            } catch (IOException ex) {
                log.warn("Failed to write agent step log to {}", logFilePath, ex);
            }
        }
    }

    private JsonNode toJsonNode(Object value) {
        return value == null ? objectMapper.nullNode() : objectMapper.valueToTree(value);
    }
}
