package com.llmcr.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadAllConfigSections() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, sampleYaml());

        ConfigReader reader = new ConfigReader(configPath.toString());
        ApplicationProperties config = reader.applicationConfiguration();

        assertNotNull(config);
        assertNotNull(config.getTrackRoots());
        assertEquals(2, config.getTrackRoots().size());
        assertEquals("spring-ai-main-code", config.getTrackRoots().get(0).getId());
        assertEquals("../_datasets/projects/spring-ai-main/", config.getTrackRoots().get(0).getPath());
        assertEquals(1, config.getTrackRoots().get(0).getAllowedSourceTypes().size());
        assertEquals("JAVACODE", config.getTrackRoots().get(0).getAllowedSourceTypes().get(0).name());

        assertNotNull(config.getCollections());
        assertEquals(3, config.getCollections().size());
        assertEquals(List.of("spring-ai-main-code"), config.getCollections().get("project-code").getTrackRoots());
        assertEquals(
                List.of("spring-ai-main-code", "spring-ai-main-docs"),
                config.getCollections().get("project-context").getTrackRoots());

        assertNotNull(config.getModels());
        assertEquals(5, config.getModels().size());
        assertEquals("openai", config.getModels().get("nemotron-3-4b").getProvider());
        assertEquals(
                ApplicationProperties.ModelType.CHAT_MODEL,
                config.getModels().get("nemotron-3-4b").getType());

        ApplicationProperties.ModelProperties expectedModel = config.getModels().get("nemotron-3-4b");
        ApplicationProperties.AgentProperties qaAgent = config.getAgents().get("questionAnswering");
        ApplicationProperties.AgentProperties interpretationAgent = config.getAgents().get("interpretation");

        assertNotNull(expectedModel);
        assertNotNull(qaAgent);
        assertNotNull(qaAgent.getChatModelProperties());
        assertSame(expectedModel, qaAgent.getChatModelProperties());
        assertEquals("nemotron-3-4b", qaAgent.getChatModelProperties().getName());
        assertEquals("all", qaAgent.getCollection());

        assertNotNull(interpretationAgent);
        assertSame(config.getModels().get("gemini-2.5-flash-lite"), interpretationAgent.getChatModelProperties());
        assertEquals("project-context", interpretationAgent.getCollection());

        assertNotNull(config.getLogging());
        assertEquals("/reviews", config.getLogging().getReviewOutputDir());
    }

    @Test
    void shouldModifyAndPersistAllSections() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, sampleYaml());

        ConfigReader reader = new ConfigReader(configPath.toString());
        ApplicationProperties config = reader.applicationConfiguration();

        config.getTrackRoots().get(0).setPath("../changed/path/");
        config.getTrackRoots().get(0).setAllowedSourceTypes(List.of());

        config.getCollections().get("project-code").setTrackRoots(List.of("spring-ai-main-docs"));

        config.getModels().get("phi-4-mini").setProvider("azure-openai");
        config.getModels().get("qwen3-reranker-0.6b").setType(ApplicationProperties.ModelType.CHAT_MODEL);

        config.getAgents().get("questionAnswering")
                .setChatModelProperties(config.getModels().get("gemini-2.5-flash-lite"));
        config.getAgents().get("questionAnswering").setCollection("guidelines");

        config.getLogging().setReviewOutputDir("/tmp/reviews");

        reader.save(config);

        ApplicationProperties reloaded = reader.applicationConfiguration();

        assertEquals("../changed/path/", reloaded.getTrackRoots().get(0).getPath());
        assertTrue(reloaded.getTrackRoots().get(0).getAllowedSourceTypes().isEmpty());
        assertEquals(
                List.of("spring-ai-main-docs"),
                reloaded.getCollections().get("project-code").getTrackRoots());
        assertEquals("azure-openai", reloaded.getModels().get("phi-4-mini").getProvider());
        assertEquals(
                ApplicationProperties.ModelType.CHAT_MODEL,
                reloaded.getModels().get("qwen3-reranker-0.6b").getType());
        assertSame(
                reloaded.getModels().get("gemini-2.5-flash-lite"),
                reloaded.getAgents().get("questionAnswering").getChatModelProperties());
        assertEquals("guidelines", reloaded.getAgents().get("questionAnswering").getCollection());
        assertEquals("/tmp/reviews", reloaded.getLogging().getReviewOutputDir());
    }

    @Test
    void shouldSaveAgentChatAsModelKeyInYaml() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, sampleYaml());

        ConfigReader reader = new ConfigReader(configPath.toString());
        ApplicationProperties config = reader.applicationConfiguration();

        config.getAgents().get("questionAnswering")
                .setChatModelProperties(config.getModels().get("gemini-2.5-flash-lite"));

        reader.save(config);

        String savedYaml = Files.readString(configPath);
        JsonNode savedRoot = new ObjectMapper(new YAMLFactory()).readTree(savedYaml);
        assertEquals(
                "gemini-2.5-flash-lite",
                savedRoot.path("agents").path("questionAnswering").path("chat").asText());
        assertEquals("all", savedRoot.path("agents").path("questionAnswering").path("collection").asText());

        ApplicationProperties reloaded = reader.applicationConfiguration();
        assertSame(
                reloaded.getModels().get("gemini-2.5-flash-lite"),
                reloaded.getAgents().get("questionAnswering").getChatModelProperties());
    }

    private String sampleYaml() {
        return """
                track-roots:
                  - id: "spring-ai-main-code"
                    path: "../_datasets/projects/spring-ai-main/"
                    allowed-source-types:
                      - JAVACODE

                  - id: "spring-ai-main-docs"
                    path: "../_datasets/projects/spring-ai-main/spring-ai-docs/src/main/antora/modules/ROOT/pages/"
                    allowed-source-types:
                      - ASCIIDOC

                collections:
                  "project-code":
                    track-roots:
                      - "spring-ai-main-code"

                  "project-context":
                    track-roots:
                      - "spring-ai-main-code"
                      - "spring-ai-main-docs"

                  "docs":
                    track-roots:
                      - "spring-ai-main-docs"
                      - "docs"


                models:
                  "nemotron-3-4b":
                    name: "nemotron-3-4b"
                    provider: "openai"
                    type: "CHAT_MODEL"

                  "phi-4-mini":
                    name: "phi-4-mini"
                    provider: "openai"
                    type: "CHAT_MODEL"

                  "gemini-2.5-flash-lite":
                    name: "gemini-2.5-flash-lite"
                    provider: "google"
                    type: "CHAT_MODEL"

                  "harrier-0.6b":
                    name: "harrier-0.6b"
                    provider: "openai"
                    type: "EMBEDDING_MODEL"

                  "qwen3-reranker-0.6b":
                    name: "qwen3-reranker-0.6b"
                    provider: "openai"
                    type: "RERANKING_MODEL"

                agents:
                  questionAnswering:
                    chat: "nemotron-3-4b"
                    collection: "all"

                  interpretation:
                    chat: "gemini-2.5-flash-lite"
                    collection: "project-context"

                logging:
                  review-output-dir: "/reviews"
                                """;
    }
}
