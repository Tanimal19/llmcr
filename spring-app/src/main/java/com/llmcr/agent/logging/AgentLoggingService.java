package com.llmcr.agent.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.llmcr.config.ApplicationProperties;

import jakarta.annotation.PostConstruct;

@Service
public class AgentLoggingService {

    private static final Logger logger = LoggerFactory.getLogger(AgentLoggingService.class);
    private static final ObjectMapper objectMapper = buildObjectMapper();

    private static ObjectMapper buildObjectMapper() {
        SimpleModule fallbackModule = new SimpleModule();
        fallbackModule.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config,
                    BeanDescription beanDesc, JsonSerializer<?> serializer) {
                // If the bean has no serializable properties, fall back to toString()
                if (serializer.getClass().getName().contains("UnknownSerializer")) {
                    return new JsonSerializer<Object>() {
                        @Override
                        public void serialize(Object value, JsonGenerator gen,
                                SerializerProvider provider) throws java.io.IOException {
                            gen.writeString(value.getClass().getSimpleName() + "(" + value + ")");
                        }
                    };
                }
                return serializer;
            }
        });

        return new ObjectMapper()
                .disable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .findAndRegisterModules()
                .registerModule(fallbackModule);
    }

    private final Path agentLogFilePath;

    public AgentLoggingService(ApplicationProperties applicationProperties) {
        this.agentLogFilePath = Paths
                .get(applicationProperties.getLogging().getReviewOutputDir() + "/agent_logs.json");
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
