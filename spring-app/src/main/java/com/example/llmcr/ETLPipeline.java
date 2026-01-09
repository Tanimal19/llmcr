package com.example.llmcr;

import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.extractor.ClassNodeExtractor;
import com.example.llmcr.extractor.DocumentParagraphExtractor;
import com.example.llmcr.repository.DataStore;
import com.example.llmcr.utils.BatchUtils;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
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
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template(
                    """
                            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and related documentation contents.
                            You should generate a summary that contains below information:
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

    private final RateLimiter transformRateLimiter = RateLimiter.create(3.0 / 60.0);
    private final RateLimiter loadRateLimiter = RateLimiter.create(10.0 / 60.0);
    private static final int LOAD_BATCH_SIZE = 8;

    private record ClassNodeSummary(
            String description,
            String usage,
            String relationship) {
    }

    public ETLPipeline(
            DataStore dataStore,
            VectorStore vectorStore,
            ChatModel chatModel) {
        this.dataStore = dataStore;
        this.vectorStore = vectorStore;
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
            transformRateLimiter.acquire();
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
                .findAllDocumentParagraphsByKeywords(keywords, 10);
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

    /**
     * Load ClassNodes into the vector database for RAG retrieval.
     */
    public void load() {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data loading into VectorStore...");

        // class node embeddings
        List<ClassNode> processedNodes = dataStore.findAllClassNodes().stream()
                .filter(ClassNode::isProcessed)
                .collect(Collectors.toList());
        List<Document> classDocuments = new ArrayList<>();

        for (ClassNode classNode : processedNodes) {
            String class_code = String.format(
                    "Class: %s\nCode: %s",
                    classNode.getSignature(),
                    classNode.getCodeText());

            String class_summary = String.format(
                    "Class: %s\nDescription: %s\nExample Usage: %s\nRelationship: %s",
                    classNode.getSignature(),
                    classNode.getDescriptionText(),
                    classNode.getUsageText(),
                    classNode.getRelationshipText());

            classDocuments.add(createDoc(class_code, classNode.getSignature(),
                    "class_code"));
            classDocuments.add(createDoc(class_summary, classNode.getSignature(),
                    "class_summary"));
        }
        loadBatch(classDocuments);

        // document paragraph embeddings
        // List<Document> paragraphDocuments =
        // dataStore.findAllDocumentParagraphs().stream()
        // .limit(100)
        // .map(paragraph -> {
        // return createDoc(
        // paragraph.getContent(),
        // paragraph.getSource(),
        // "document_paragraph");
        // })
        // .collect(Collectors.toList());
        // loadBatch(paragraphDocuments);

        long endTime = System.currentTimeMillis();
        System.out.println("+ Loading completed in " + (endTime - startTime) + "ms");
    }

    private Document createDoc(String content, String source, String type) {
        Document doc = new Document(content);
        doc.getMetadata().put("source", source);
        doc.getMetadata().put("type", type);
        return doc;
    }

    private void loadBatch(List<Document> documents) {
        List<List<Document>> batches = BatchUtils.batch(documents, LOAD_BATCH_SIZE);

        for (List<Document> batch : batches) {
            System.out.println("+ Loading " + batch.size() + " documents into VectorStore...");
            loadRateLimiter.acquire();
            vectorStore.add(batch);
        }
    }
}
