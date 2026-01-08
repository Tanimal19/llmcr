package com.example.llmcr.service;

import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.extractor.ClassNodeExtractor;
import com.example.llmcr.extractor.DocumentParagraphExtractor;
import com.example.llmcr.repository.DataStore;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.errors.ClientException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.util.concurrent.RateLimiter;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the Extract, Transform, and Load process.
 */
@Service
public class ETLService {

    private final List<DataSource> rawDataSources;
    private final DataStore dataStore;
    private final VectorStore vectorStore;
    private final ClassNodeSummaryService classNodeSummaryService;

    public record ClassNodeSummary(
            String summary,
            String exampleUsage,
            String relationship) {
    }

    public record ContentPiece(String content, String type) {
    }

    public ETLService(
            List<DataSource> rawDataSources,
            DataStore dataStore,
            VectorStore vectorStore,
            ChatModel chatModel) {
        this.rawDataSources = rawDataSources;
        this.dataStore = dataStore;
        this.vectorStore = vectorStore;
        this.classNodeSummaryService = new ClassNodeSummaryService(chatModel, 4);
    }

    /**
     * Extract ClassNodes and DocumentParagraphs from all datasources.
     */
    public ETLService extract() {
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

        return this;
    }

    /**
     * Transform ClassNodes by generating summaries using LLM.
     */
    public ETLService transform() {
        System.out.println("+ Starting data transformation...");
        List<ClassNode> unprocessedNodes = dataStore.findUnprocessedClassNodes();

        for (ClassNode classNode : unprocessedNodes) {
            classNode = bindNodeWithParagraphs(classNode);
            classNode = enrichNodeWithSummary(classNode);
            classNode.setProcessed(true);
            dataStore.save(classNode);
        }

        return this;
    }

    private ClassNode bindNodeWithParagraphs(ClassNode classNode) {
        List<String> keywords = extractKeywords(classNode.getSignature());
        List<DocumentParagraph> relevantParagraphs = dataStore
                .findAllDocumentParagraphsByKeywords(keywords);
        classNode.setDocumentParagraphs(relevantParagraphs);
        System.out.println(
                "Bound " + relevantParagraphs.size() + " paragraphs to ClassNode: " + classNode.getSignature()
                        + " using keywords: " + keywords);
        return classNode;
    }

    private List<String> extractKeywords(String signature) {
        List<String> keywords = new ArrayList<>();
        String[] parts = signature.split("\\.");

        keywords.add(parts[parts.length - 1]); // class name
        keywords.add(parts[parts.length - 2]); // package name

        return keywords;
    }

    private ClassNode enrichNodeWithSummary(ClassNode classNode) {
        System.out.println("Generating summary for ClassNode: " + classNode.getSignature());
        ClassNodeSummary summary = classNodeSummaryService.summarize(
                classNode.getCodeText(),
                classNode.getDocumentParagraphs().stream()
                        .map(DocumentParagraph::getContent)
                        .collect(Collectors.joining("\n")));

        classNode.setSummaryText(summary.summary());
        classNode.setUsageText(summary.exampleUsage());
        classNode.setRelationshipText(summary.relationship());
        return classNode;
    }

    /**
     * Load ClassNodes into the vector database for RAG retrieval.
     */
    public void load() {
        List<ClassNode> processedNodes = dataStore.findAllClassNodes().stream()
                .filter(ClassNode::isProcessed)
                .collect(Collectors.toList());

        List<Document> documents = new ArrayList<>();

        for (ClassNode classNode : processedNodes) {
            // Create documents
            String content_with_summary = String.format(
                    "Class: %s\nCode: %s\nSummary: %s",
                    classNode.getSignature(),
                    classNode.getCodeText(),
                    classNode.getSummaryText());
            String content_with_usage = String.format(
                    "Class: %s\nCode: %s\nExample Usage: %s",
                    classNode.getSignature(),
                    classNode.getCodeText(),
                    classNode.getUsageText());
            String content_with_relationship = String.format(
                    "Class: %s\nCode: %s\nRelationship: %s",
                    classNode.getSignature(),
                    classNode.getCodeText(),
                    classNode.getRelationshipText());

            List<ContentPiece> contentPieces = List.of(
                    new ContentPiece(content_with_summary, "summary"),
                    new ContentPiece(content_with_usage, "exampleUsage"),
                    new ContentPiece(content_with_relationship, "relationship"));

            for (ContentPiece contentPiece : contentPieces) {
                Document doc = new Document(contentPiece.content);
                doc.getMetadata().put("classNodeId", classNode.getId());
                doc.getMetadata().put("type", contentPiece.type);
                documents.add(doc);
            }
        }

        // Store in vector database
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    private class ClassNodeSummaryService {
        private final ChatModel chatModel;
        private final RateLimiter rateLimiter;
        private final PromptTemplate promptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(
                        """
                                You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and related documentation contents.
                                You should generate a summary that contains below information:
                                - **summary**: what does this class do?
                                - **exampleUsage**: best practices of this class, only include the most important examples.
                                - **relationship**: how does this class relate to other classes or components in the documentation?

                                Raw code:
                                <code>
                                Documentation contents:
                                <doc>

                                <format>
                                    """)
                .build();
        private final BeanOutputConverter<ClassNodeSummary> outputConverter = new BeanOutputConverter<>(
                ClassNodeSummary.class);

        ClassNodeSummaryService(ChatModel chatModel, int requestsPerMinute) {
            this.chatModel = chatModel;
            // Convert requests per minute to permits per second
            this.rateLimiter = RateLimiter.create(requestsPerMinute / 60.0);
            System.out.println("Rate limiter initialized: " + requestsPerMinute + " requests per minute");
        }

        public ClassNodeSummary summarize(String rawCode, String documentation) {
            // perpare prompt
            String formatInstruction = outputConverter.getFormat();
            Map<String, Object> variables = Map.of(
                    "code", rawCode,
                    "doc", documentation,
                    "format", formatInstruction);
            Prompt prompt = promptTemplate.create(variables);

            // apply rate limiting
            rateLimiter.acquire();
            System.out.println("Rate limiter: request acquired");

            // call chat model
            try {
                ChatResponse response = chatModel.call(prompt);
                String rawOutput = response.getResult().getOutput().getText();
                ClassNodeSummary result = outputConverter.convert(rawOutput);

                appendToJsonFile(prompt.getContents(), result);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to summarize class node: " + e.getMessage(), e);
            }
        }

        private void appendToJsonFile(String prompt, ClassNodeSummary result) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                File file = new File("summaries.json");

                ArrayNode array;
                if (file.exists()) {
                    array = (ArrayNode) mapper.readTree(file);
                } else {
                    array = mapper.createArrayNode();
                }

                ObjectNode entry = mapper.createObjectNode();
                entry.put("timestamp", java.time.Instant.now().toString());
                entry.put("prompt", prompt);
                entry.put("summary", result.summary());
                entry.put("exampleUsage", result.exampleUsage());
                entry.put("relationship", result.relationship());

                array.add(entry);
                mapper.writeValue(file, array);
            } catch (IOException e) {
                System.err.println("Failed to append to summaries.json: " + e.getMessage());
            }
        }
    }
}
