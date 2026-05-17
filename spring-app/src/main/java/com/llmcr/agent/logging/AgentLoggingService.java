package com.llmcr.agent.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.annotation.PostConstruct;

@Service
public class AgentLoggingService {

    private static final Logger logger = LoggerFactory.getLogger(AgentLoggingService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT)
            .findAndRegisterModules();

    private final Path agentLogFilePath;

    public AgentLoggingService(@Value("${llmcr.agent.log.file}") String agentLogFile) {
        this.agentLogFilePath = agentLogFile != null && !agentLogFile.isBlank() ? Paths.get(agentLogFile) : null;
    }

    @PostConstruct
    public void init() {
        if (agentLogFilePath != null) {
            try {
                Files.createDirectories(agentLogFilePath.getParent());
                logger.debug("Agent logging service initialized with file: {}", agentLogFilePath);
                // Register this service's logging function to context holder
                AgentContextHolder.setOnContextFinished(this::logAgentExecution);
            } catch (IOException e) {
                logger.warn("Failed to initialize agent log file path: {}", agentLogFilePath, e);
            }
        } else {
            logger.debug("Agent logging disabled (llmcr.agent.log.file not configured)");
        }
    }

    public void logAgentExecution(AgentCallEntry entry) {
        if (agentLogFilePath == null || entry == null) {
            return;
        }

        try {
            String jsonLine = objectMapper.writeValueAsString(entry) + "\n";
            Files.write(agentLogFilePath, jsonLine.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            logger.debug("Logged agent execution for: {}", entry.agentName);
        } catch (IOException e) {
            logger.error("Failed to write agent log entry to {}", agentLogFilePath, e);
        }
    }
}
