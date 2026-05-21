package com.llmcr.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.llmcr.entity.Source.SourceType;

class ConfigReaderTest {

    @Test
    void shouldReadConfigYmlAndBindAgentChatModelProperties() {
        Path configPath = Path.of("config.yml").toAbsolutePath();
        assertTrue(configPath.toFile().exists(), "Expected config.yml at: " + configPath);

        ConfigReader configReader = new ConfigReader(configPath.toString());
        ApplicationProperties config = configReader.applicationConfiguration();

        assertNotNull(config);

        ApplicationProperties.TrackRootProperties springCode = config.getTrackRoots().get("spring-ai-main-code");
        assertNotNull(springCode);
        assertEquals("spring-ai-main-code", springCode.getId());
        assertEquals("../_datasets/projects/spring-ai-main/", springCode.getPath());
        assertEquals(List.of(SourceType.JAVACODE), springCode.getAllowedSourceTypes());

        ApplicationProperties.CollectionProperties projectContext = config.getCollections().get("project-context");
        assertNotNull(projectContext);
        assertEquals(List.of("spring-ai-main-code", "spring-ai-main-docs"), projectContext.getTrackRoots());

        ApplicationProperties.AgentProperties interpretation = config.getAgents().get("interpretation");
        assertNotNull(interpretation);
        assertEquals("gemini-2.5-flash-lite", interpretation.getChat());
        assertEquals("google", interpretation.getChatModelProperties().getProvider());
        assertEquals("gemini-2.5-flash-lite", interpretation.getChatModelProperties().getName());

        ApplicationProperties.AgentProperties retrieval = config.getAgents().get("retrieval");
        assertNotNull(retrieval);
        assertEquals("nemotron-3-4b", retrieval.getChat());
        assertEquals("openai", retrieval.getChatModelProperties().getProvider());
        assertEquals("nemotron-3-4b", retrieval.getChatModelProperties().getName());

        assertEquals("../logs/reviews", config.getLogging().getReviewOutputDir());
    }
}
