package com.llmcr.infrastructure.agent.logging;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.llmcr.config.provider.LoggingConfigProvider;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgentContextLogger {

    private static final Logger logger = LoggerFactory.getLogger(AgentContextLogger.class);
    private static final ObjectMapper objectMapper = buildObjectMapper();
    private final Object fileWriteLock = new Object();

    private static ObjectMapper buildObjectMapper() {
        SimpleModule fallbackModule = new SimpleModule();
        fallbackModule.setSerializerModifier(
                new BeanSerializerModifier() {
                    @Override
                    public JsonSerializer<?> modifySerializer(
                            SerializationConfig config,
                            BeanDescription beanDesc,
                            JsonSerializer<?> serializer) {
                        // If the bean has no serializable properties, fall back to toString()
                        if (serializer.getClass().getName().contains("UnknownSerializer")) {
                            return new JsonSerializer<Object>() {
                                @Override
                                public void serialize(Object value, JsonGenerator gen, SerializerProvider provider)
                                        throws IOException {
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

    public static final String DEFAULT_LOG_FILE_NAME = "agent_history.json";
    private final Path logFilePath;

    public AgentContextLogger(LoggingConfigProvider loggingConfigProvider) {
        this.logFilePath = Paths.get(loggingConfigProvider.getReviewOutputDirectory(), DEFAULT_LOG_FILE_NAME);
    }

    @PostConstruct
    public void init() {
        if (logFilePath != null) {
            try {
                Files.createDirectories(logFilePath.getParent());
                initializeLogFile();
                logger.debug("Agent logging service initialized with file: {}", logFilePath);
                // Register this service's logging function to context holder
                AgentContextHolder.setOnContextFinished(this::logAgentExecution);
            } catch (IOException e) {
                logger.warn("Failed to initialize agent log file path: {}", logFilePath, e);
            }
        } else {
            logger.debug("Agent logging disabled (llmcr.agent.log.file not configured)");
        }
    }

    public void logAgentExecution(AgentCallContext entry) {
        if (logFilePath == null || entry == null) {
            return;
        }

        try {
            appendEntry(entry);
            logger.debug("Logged agent execution for: {}", entry.agentName);
        } catch (IOException e) {
            logger.error("Failed to write agent log entry to {}", logFilePath, e);
        }
    }

    private void initializeLogFile() throws IOException {
        if (!Files.exists(logFilePath) || Files.size(logFilePath) == 0) {
            Files.writeString(
                    logFilePath,
                    "[]\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }
    }

    private void appendEntry(AgentCallContext entry) throws IOException {
        String serializedEntry = objectMapper.writeValueAsString(entry);

        synchronized (fileWriteLock) {
            initializeLogFile();

            String currentContent = Files.readString(logFilePath).trim();
            String updatedContent;

            if (currentContent.isEmpty() || "[]".equals(currentContent)) {
                updatedContent = "[\n" + serializedEntry + "\n]\n";
            } else if (currentContent.endsWith("]")) {
                String contentWithoutClosingBracket = currentContent.substring(0, currentContent.length() - 1)
                        .stripTrailing();
                updatedContent = contentWithoutClosingBracket + ",\n" + serializedEntry + "\n]\n";
            } else {
                updatedContent = "[\n" + serializedEntry + "\n]\n";
                logger.warn("Agent log file {} was not valid JSON array content. It has been reset.", logFilePath);
            }

            Files.writeString(
                    logFilePath,
                    updatedContent,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }
    }
}
