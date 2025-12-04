package com.example.llmcr.pipeline;

import com.example.llmcr.datasource.RawDataSource;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.extractor.ClassNodeExtractor;
import com.example.llmcr.extractor.DocumentParagraphExtractor;
import com.example.llmcr.repository.DataStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the Extract, Transform, and Load process.
 */
@Component
public class ETLPipeline {

    private final List<RawDataSource> rawDataSources;
    private final DataStore dataStore;
    private final VectorStore vectorStore;
    private final ChatClient summaryClient;

    private final ChatClient

    public ETLPipeline(
            List<RawDataSource> rawDataSources,
            DataStore dataStore,
            VectorStore vectorStore) {
        this.rawDataSources = rawDataSources;
        this.dataStore = dataStore;
        this.vectorStore = vectorStore;
        this.chatClient = new ChatClient();
    }

    /**
     * Extract ClassNodes and DocumentParagraphs from all datasources.
     */
    public ETLPipeline extract() {
        ClassNodeExtractor classNodeExtractor = new ClassNodeExtractor();
        DocumentParagraphExtractor documentParagraphExtractor = new DocumentParagraphExtractor();

        List<ClassNode> allClassNodes = new ArrayList<>();
        List<DocumentParagraph> allDocumentParagraphs = new ArrayList<>();

        // Iterate over all raw data sources and extract data
        for (RawDataSource source : rawDataSources) {
            List<ClassNode> classNodes = source.accept(classNodeExtractor);
            allClassNodes.addAll(classNodes);

            List<DocumentParagraph> paragraphs = source.accept(documentParagraphExtractor);
            allDocumentParagraphs.addAll(paragraphs);
        }

        // Persist extracted data
        this.dataStore.saveAllClassNodes(allClassNodes);
        this.dataStore.saveAllDocumentParagraphs(allDocumentParagraphs);

        return this;
    }

    /**
     * Transform ClassNodes by generating summaries using LLM.
     */
    public ETLPipeline transform() {
        List<ClassNode> unprocessedNodes = dataStore.findUnprocessedClassNodes();

        for (ClassNode classNode : unprocessedNodes) {
            bindNodeWithParagraphs(classNode);

            String summary = generateSummary(classNode);
            classNode.setSummaryText(summary);
            classNode.setProcessed(true);

            dataStore.save(classNode);
        }

        return this;
    }

    /**
     * Bind ClassNode with its related DocumentParagraphs.
     */
    public ClassNode bindNodeWithParagraphs(ClassNode classNode) {
        List<DocumentParagraph> relevantParagraphs = dataStore
                .findAllDocumentParagraphsByKeyword(classNode.getSignature());
        classNode.getDocumentParagraphs().addAll(relevantParagraphs);
        return classNode;
    }

    /**
     * Generate summary for a ClassNode using LLM.
     */
    public String generateSummary(ClassNode classNode) {
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Class Signature: ").append(classNode.getSignature()).append("\n");
        contextBuilder.append("Code: ").append(classNode.getCodeText()).append("\n");

        // Add related documentation paragraphs
        if (!classNode.getDocumentParagraphs().isEmpty()) {
            contextBuilder.append("Related Documentation:\n");
            for (DocumentParagraph paragraph : classNode.getDocumentParagraphs()) {
                contextBuilder.append("- ").append(paragraph.getContent()).append("\n");
            }
        }

        String prompt = "Please provide a comprehensive summary of the following class including its purpose, functionality, and key features:\n\n"
                + contextBuilder.toString();

        try {
            return chatClient.prompt(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            System.err
                    .println("Error generating summary for class: " + classNode.getSignature() + ", " + e.getMessage());
            return "Summary generation failed: " + e.getMessage();
        }
    }

    /**
     * Generate summary for a list of ClassNodes.
     */
    public String generateSummary(List<ClassNode> classNodes) {
        StringBuilder contextBuilder = new StringBuilder();

        for (ClassNode classNode : classNodes) {
            contextBuilder.append("Class: ").append(classNode.getSignature()).append("\n");
            contextBuilder.append("Summary: ").append(classNode.getSummaryText()).append("\n\n");
        }

        String prompt = "Please provide an overall summary of the following classes and their relationships:\n\n"
                + contextBuilder.toString();

        try {
            return chatClient.prompt(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            System.err.println("Error generating overall summary: " + e.getMessage());
            return "Overall summary generation failed: " + e.getMessage();
        }
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
            // Create a document for vector storage
            String content = String.format(
                    "Class: %s\nCode: %s\nSummary: %s",
                    classNode.getSignature(),
                    classNode.getCodeText(),
                    classNode.getSummaryText());

            Document document = new Document(content);
            document.getMetadata().put("classId", classNode.getId());
            document.getMetadata().put("signature", classNode.getSignature());
            documents.add(document);
        }

        // Store in vector database
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    /**
     * Execute the complete ETL pipeline.
     */
    public ETLPipeline execute() {
        // Extract
        List<ClassNode> classNodes = extract();
        dataStore.saveAllClassNodes(classNodes);

        // Transform
        transform();

        // Load
        load();

        return this;
    }

    // Getters
    public List<RawDataSource> getRawDataSources() {
        return rawDataSources;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }
}