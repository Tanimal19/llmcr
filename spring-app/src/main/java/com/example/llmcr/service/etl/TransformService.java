package com.example.llmcr.service.etl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;

import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.utils.JsonBackupUtils;
import com.google.common.util.concurrent.RateLimiter;

public class TransformService {

    private static final String transformChatHistoryFile = "transform_history.json";

    private static final PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template(
                    """
                            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and related documentation contents.
                            You should generate below information:
                            - **description**: What does this class do?
                            - **exampleUsage**: Best practices of this class, only include the one most important examples. Illustrate the example with natural language explanation, not code.
                            - **relationship**: How does this class relate to other classes or components in the project?

                            Raw code at below.
                            -----------------
                            <code>
                            -----------------

                            Documentation contents at below.
                            -----------------
                            <doc>
                            -----------------

                            <format>
                                """)
            .build();
    private static final BeanOutputConverter<ClassNodeSummary> outputConverter = new BeanOutputConverter<>(
            ClassNodeSummary.class);

    private static final RateLimiter rateLimiter = RateLimiter.create(30.0 / 60.0);

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformService.class);

    private record ClassNodeSummary(
            String description,
            String usage,
            String relationship) {
    }

    private TransformService() {
    }

    public static void enrich(DataStore dataStore, ChatModel chatModel, int maxParagraphsPerNode) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Start data enrichment");

        dataStore.findUnprocessedClassNodes().stream().forEach(classNode -> {
            // bind node with related paragraphs
            List<String> keywords = new ArrayList<>();
            String[] parts = classNode.getSignature().split("\\.");
            keywords.add(parts[parts.length - 1]); // class name
            keywords.add(parts[parts.length - 2]); // package name

            List<DocumentParagraph> relevantParagraphs = dataStore
                    .findAllDocumentParagraphsByKeywords(keywords, maxParagraphsPerNode);
            classNode.setDocumentParagraphs(relevantParagraphs);
            LOGGER.info("Bound " + relevantParagraphs.size() + " paragraphs to ClassNode: "
                    + classNode.getSignature()
                    + " using keywords: " + keywords);

            // enrich node with llm generated summary
            // build prompt
            String code = Objects.toString(classNode.getContent(), "");
            List<DocumentParagraph> paragraphs = classNode.getDocumentParagraphs();
            String doc = paragraphs == null ? ""
                    : paragraphs.stream()
                            .map(DocumentParagraph::getContent)
                            .collect(Collectors.joining("\n"));
            String formatInstruction = Objects.toString(outputConverter.getFormat(), "");
            Map<String, Object> variables = Map.of(
                    "code", code,
                    "doc", doc,
                    "format", formatInstruction);
            Prompt prompt = promptTemplate.create(variables);

            // call chat model
            ChatResponse response;
            int count = 0;
            while (true) {
                rateLimiter.acquire();
                try {
                    response = chatModel.call(prompt);
                    break;
                } catch (Exception e) {
                    LOGGER.warn("Chat model call failed: " + e.getMessage());
                }

                count++;
                LOGGER.warn("Retry: attempt #" + count);
                if (count > 5) {
                    throw new RuntimeException("Failed to call chat model.");
                }
            }

            String rawResponse = response.getResult().getOutput().getText();
            ClassNodeSummary nodeSummary;
            try {
                nodeSummary = outputConverter.convert(rawResponse);
            } catch (Exception e) {
                LOGGER.warn("Output conversion failed for ClassNode: "
                        + classNode.getSignature()
                        + ". Error: " + e.getMessage());
                nodeSummary = new ClassNodeSummary(
                        rawResponse,
                        "null",
                        "null");
            }

            // update class node
            classNode.setDescriptionText(nodeSummary.description());
            classNode.setUsageText(nodeSummary.usage());
            classNode.setRelationshipText(nodeSummary.relationship());

            // backup generated summary into json
            try {
                Map<String, Object> entry = Map.of(
                        "timestamp", java.time.Instant.now().toString(),
                        "prompt", Objects.toString(prompt, ""),
                        "description", Objects.toString(nodeSummary.description(), ""),
                        "exampleUsage", Objects.toString(nodeSummary.usage(), ""),
                        "relationship", Objects.toString(nodeSummary.relationship(), ""));
                JsonBackupUtils.appendJsonBackup(transformChatHistoryFile, entry);
            } catch (IOException e) {
                LOGGER.warn("Failed to save transform history for ClassNode: "
                        + classNode.getSignature()
                        + ". Error: " + e.getMessage());
            }

            LOGGER.info("Enriched ClassNode: " + classNode.getSignature());

            classNode.setProcessed(true);
            dataStore.save(classNode);
        });

        long endTime = System.currentTimeMillis();
        LOGGER.info("Data enrichment completed in " + (endTime - startTime) + "ms");
    }
}
