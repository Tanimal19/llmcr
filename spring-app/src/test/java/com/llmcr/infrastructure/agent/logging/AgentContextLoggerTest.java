package com.llmcr.infrastructure.agent.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmcr.config.provider.LoggingConfigProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentContextLoggerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void logAgentExecutionWritesMultipleEntriesAsValidJsonArray() throws IOException {
        LoggingConfigProvider loggingConfigProvider = mock(LoggingConfigProvider.class);
        when(loggingConfigProvider.getReviewOutputDirectory()).thenReturn(tempDir.toString());

        AgentContextLogger logger = new AgentContextLogger(loggingConfigProvider);
        logger.init();

        logger.logAgentExecution(createEntry("agent-one", "model-a"));
        logger.logAgentExecution(createEntry("agent-two", "model-b"));

        Path logFile = tempDir.resolve(AgentContextLogger.DEFAULT_LOG_FILE_NAME);
        JsonNode loggedEntries = objectMapper.readTree(Files.readString(logFile));

        assertThat(loggedEntries.isArray()).isTrue();
        assertThat(loggedEntries).hasSize(2);
        assertThat(loggedEntries.get(0).path("agentName").asText()).isEqualTo("agent-one");
        assertThat(loggedEntries.get(1).path("agentName").asText()).isEqualTo("agent-two");
    }

    private AgentCallContext createEntry(String agentName, String modelName) {
        AgentCallContext entry = new AgentCallContext();
        entry.agentName = agentName;
        entry.modelName = modelName;
        entry.startedAt = 1L;
        entry.endedAt = 2L;
        entry.durationMs = 1L;
        return entry;
    }
}