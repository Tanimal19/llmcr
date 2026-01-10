package com.example.llmcr;

import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.extractor.ClassNodeExtractor;
import com.example.llmcr.extractor.DocumentParagraphExtractor;
import com.example.llmcr.repository.DataStore;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.RateLimiter;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the Extract, Transform, and Load process.
 */
public class ETLPipeline {

    private final DataStore dataStore;
    private final ChatModel chatModel;

    private final int MAX_PARAGRAPHS_PER_NODE = 10;

    private final PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template(
                    """
                            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and related documentation contents.
                            You should generate below information:
                            - **description**: what does this class do?
                            - **exampleUsage**: best practices of this class, only include the most important examples.
                            - **relationship**: how does this class relate to other classes or components in the project?

                            Raw code:
                            <code>
                            Documentation contents:
                            <doc>

                            <format>
                                """)
            .build();
    private final BeanOutputConverter<ClassNodeSummary> outputConverter = new BeanOutputConverter<>(
            ClassNodeSummary.class);
    private final String summaryBackupFile = "summaries.json";

    private final RateLimiter rateLimiter = RateLimiter.create(3.0 / 60.0);

    private record ClassNodeSummary(
            String description,
            String usage,
            String relationship) {
    }

    public ETLPipeline(
            DataStore dataStore,
            ChatModel chatModel) {
        this.dataStore = dataStore;
        this.chatModel = chatModel;
    }

    /**
     * Extract ClassNodes and DocumentParagraphs from all datasources.
     */
    public ETLPipeline extract(List<DataSource> rawDataSources) {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data extraction...");

        ClassNodeExtractor classNodeExtractor = new ClassNodeExtractor();
        DocumentParagraphExtractor documentParagraphExtractor = new DocumentParagraphExtractor();

        List<ClassNode> allClassNodes = new ArrayList<>();
        List<DocumentParagraph> allDocumentParagraphs = new ArrayList<>();

        // Iterate over all raw data sources and extract data
        for (DataSource source : rawDataSources) {
            List<ClassNode> classNodes = source.accept(classNodeExtractor);
            allClassNodes.addAll(classNodes);

            List<DocumentParagraph> paragraphs = source.accept(documentParagraphExtractor);
            allDocumentParagraphs.addAll(paragraphs);
        }

        // Persist extracted data
        dataStore.saveAllClassNodes(allClassNodes);
        dataStore.saveAllDocumentParagraphs(allDocumentParagraphs);

        long endTime = System.currentTimeMillis();
        System.out.println("+ Extraction completed in " + (endTime - startTime) + "ms");

        return this;
    }

    /**
     * Transform ClassNodes by generating summaries using LLM.
     */
    public ETLPipeline transform() {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data transformation...");
        List<ClassNode> unprocessedNodes = dataStore.findUnprocessedClassNodes();

        for (ClassNode classNode : unprocessedNodes) {
            classNode = bindNodeWithParagraphs(classNode);
            classNode = enrichNodeWithSummary(classNode);
            classNode.setProcessed(true);
            dataStore.save(classNode);
            rateLimiter.acquire();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("+ Transformation completed in " + (endTime - startTime) + "ms");

        return this;
    }

    private ClassNode bindNodeWithParagraphs(ClassNode classNode) {
        List<String> keywords = new ArrayList<>();
        String[] parts = classNode.getSignature().split("\\.");
        keywords.add(parts[parts.length - 1]); // class name
        keywords.add(parts[parts.length - 2]); // package name

        List<DocumentParagraph> relevantParagraphs = dataStore
                .findAllDocumentParagraphsByKeywords(keywords, MAX_PARAGRAPHS_PER_NODE);
        classNode.setDocumentParagraphs(relevantParagraphs);
        System.out.println(
                "Bound " + relevantParagraphs.size() + " paragraphs to ClassNode: " + classNode.getSignature()
                        + " using keywords: " + keywords);
        return classNode;
    }

    private ClassNode enrichNodeWithSummary(ClassNode classNode) {
        System.out.println("Generating summary for ClassNode: " + classNode.getSignature());

        // perpare prompt
        String formatInstruction = outputConverter.getFormat();
        Map<String, Object> variables = Map.of(
                "code", classNode.getCodeText(),
                "doc", classNode.getDocumentParagraphs().stream()
                        .map(DocumentParagraph::getContent)
                        .collect(Collectors.joining("\n")),
                "format", formatInstruction);
        Prompt prompt = promptTemplate.create(variables);

        // call chat model
        ChatResponse response = chatModel.call(prompt);
        String rawOutput = response.getResult().getOutput().getText();

        ClassNodeSummary nodeSummary;
        try {
            nodeSummary = outputConverter.convert(rawOutput);
        } catch (Exception e) {
            System.err.println("Output conversion failed. Store raw output to summary. Error: " + e.getMessage());
            nodeSummary = new ClassNodeSummary(
                    rawOutput,
                    null,
                    null);
        }

        // update class node
        classNode.setDescriptionText(nodeSummary.description());
        classNode.setUsageText(nodeSummary.usage());
        classNode.setRelationshipText(nodeSummary.relationship());

        // backup generated summary into json for analysis
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            File file = new File(summaryBackupFile);

            ArrayNode array;
            if (file.exists()) {
                array = (ArrayNode) mapper.readTree(file);
            } else {
                array = mapper.createArrayNode();
            }

            ObjectNode entry = mapper.createObjectNode();
            entry.put("timestamp", java.time.Instant.now().toString());
            entry.put("prompt", prompt.toString());
            entry.put("description", nodeSummary.description());
            entry.put("exampleUsage", nodeSummary.usage());
            entry.put("relationship", nodeSummary.relationship());

            array.add(entry);
            mapper.writeValue(file, array);
        } catch (IOException e) {
            System.err.println("Failed to append to summaries.json: " + e.getMessage());
        }

        return classNode;
    }
}
