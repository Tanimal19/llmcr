package com.example.llmcr.service;

import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.entity.Chunk.ChunkType;
import com.example.llmcr.extractor.ClassNodeExtractor;
import com.example.llmcr.extractor.DocumentParagraphExtractor;
import com.example.llmcr.repository.DataStore;
import com.example.llmcr.utils.JsonBackupUtils;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.RateLimiter;

import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the Extract, Transform, and Load process.
 */
@Service
public class ETLPipeline {

    private final DataStore dataStore;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    @Value("${etl.options.extract.pdf.maxParagraphLength:4096}")
    private int maxPdfParagraphLength;

    @Value("${etl.options.extract.ascii.maxParagraphLength:4096}")
    private int maxAsciiParagraphLength;

    @Value("${etl.options.transform.maxParagraphsPerNode:10}")
    private int maxParagraphsPerNode;

    @Value("${etl.options.transform.requestPerMinute:2}")
    private int transformRpm;

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

    private record ClassNodeSummary(
            String description,
            String usage,
            String relationship) {
    }

    @Value("${etl.backup.transformChatHistoryFile:transform_history.json}")
    private String transformChatHistoryFile;

    @Autowired
    public ETLPipeline(
            DataStore dataStore,
            ChatModel chatModel,
            VectorStore vectorStore) {
        this.dataStore = dataStore;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    public ETLPipeline extract(List<DataSource> rawDataSources) {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data extraction...");
        System.out.println(
                "  - maxPdfParagraphLength: " + maxPdfParagraphLength);
        System.out.println(
                "  - maxAsciiParagraphLength: " + maxAsciiParagraphLength);

        ClassNodeExtractor classNodeExtractor = new ClassNodeExtractor();
        DocumentParagraphExtractor documentParagraphExtractor = new DocumentParagraphExtractor(
                maxPdfParagraphLength,
                maxAsciiParagraphLength);

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

    public ETLPipeline transform() {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data transformation...");
        System.out.println("  - maxParagraphsPerNode: " + maxParagraphsPerNode);
        System.out.println("  - transformRpm: " + transformRpm);

        RateLimiter rateLimiter = RateLimiter.create(transformRpm / 60.0);

        List<ClassNode> unprocessedNodes = dataStore.findUnprocessedClassNodes();
        for (ClassNode classNode : unprocessedNodes) {
            classNode.setProcessed(true);
            classNode = bindNodeWithParagraphs(classNode);
            classNode = enrichNodeWithSummary(classNode);
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
                .findAllDocumentParagraphsByKeywords(keywords, maxParagraphsPerNode);
        classNode.setDocumentParagraphs(relevantParagraphs);
        System.out.println(
                "Bound " + relevantParagraphs.size() + " paragraphs to ClassNode: "
                        + classNode.getSignature()
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
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (Exception e) {
            System.err.println("Chat model call failed for ClassNode: "
                    + classNode.getSignature()
                    + ". Error: " + e.getMessage());
            classNode.setProcessed(false);
            return classNode;
        }

        String rawOutput = response.getResult().getOutput().getText();
        ClassNodeSummary nodeSummary;
        try {
            nodeSummary = outputConverter.convert(rawOutput);
        } catch (Exception e) {
            System.err.println("Output conversion failed. Store raw output to summary. Error: "
                    + e.getMessage());
            nodeSummary = new ClassNodeSummary(
                    rawOutput,
                    null,
                    null);
        }

        // update class node
        classNode.setDescriptionText(nodeSummary.description());
        classNode.setUsageText(nodeSummary.usage());
        classNode.setRelationshipText(nodeSummary.relationship());

        // backup generated summary into json
        try {
            Map<String, Object> entry = Map.of(
                    "timestamp", java.time.Instant.now().toString(),
                    "prompt", prompt.toString(),
                    "description", nodeSummary.description(),
                    "exampleUsage", nodeSummary.usage(),
                    "relationship", nodeSummary.relationship());
            JsonBackupUtils.appendJsonBackup(transformChatHistoryFile, entry);
        } catch (IOException e) {
            System.err.println("Failed to append to summaries.json: " + e.getMessage());
        }

        return classNode;
    }

    public ETLPipeline load() {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data loading...");

        // load all class nodes
        System.out.println("  - Loading class nodes");
        dataStore.findProcessedClassNodes().stream().forEach(node -> {
            List<Document> documentsToEmbed = new ArrayList<>();
            documentsToEmbed.add(
                    new Document(node.getCodeText(),
                            Map.of("type", ChunkType.CODE, "source_id", node.getId())));
            documentsToEmbed.add(
                    new Document(node.getDescriptionText(),
                            Map.of("type", ChunkType.SUMMARY, "source_id", node.getId())));
            documentsToEmbed
                    .add(new Document(node.getUsageText(),
                            Map.of("type", ChunkType.SUMMARY, "source_id", node.getId())));
            documentsToEmbed.add(
                    new Document(node.getRelationshipText(),
                            Map.of("type", ChunkType.SUMMARY, "source_id", node.getId())));

            vectorStore.add(documentsToEmbed);
        });

        // load all document paragraphs
        System.out.println("  - Loading document paragraphs");
        TokenTextSplitter splitter = new TokenTextSplitter();
        dataStore.findAllDocumentParagraphs().stream().forEach(paragraph -> {
            Document doc = new Document(paragraph.getContent(),
                    Map.of("type", ChunkType.PARAGRAPH, "source_id", paragraph.getId()));
            List<Document> splitDocs = splitter.split(doc);
            vectorStore.add(splitDocs);
        });

        long endTime = System.currentTimeMillis();
        System.out.println("+ Loading completed in " + (endTime - startTime) + "ms");

        return this;
    }
}
