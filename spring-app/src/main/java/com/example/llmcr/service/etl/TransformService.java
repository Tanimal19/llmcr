package com.example.llmcr.service.etl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.transformer.splitter.TextSplitter;

import com.example.llmcr.entity.Chunk;
import com.example.llmcr.entity.Chunk.ChunkType;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.repository.DataStore;
import com.example.llmcr.utils.JsonBackupUtils;
import com.google.common.util.concurrent.RateLimiter;

public class TransformService {

    private final DataStore dataStore;
    private final ChatModel chatModel;
    private String transformChatHistoryFile = "transform_history.json";

    private final PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template(
                    """
                            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and related documentation contents.
                            You should generate below information:
                            - **description**: what does this class do?
                            - **exampleUsage**: best practices of this class, only include the most important examples.
                            - **relationship**: how does this class relate to other classes or components in the project?

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
    private final BeanOutputConverter<ClassNodeSummary> outputConverter = new BeanOutputConverter<>(
            ClassNodeSummary.class);

    private record ClassNodeSummary(
            String description,
            String usage,
            String relationship) {
    }

    public TransformService(DataStore dataStore, ChatModel chatModel) {
        this.dataStore = dataStore;
        this.chatModel = chatModel;
    }

    public void enrich(int maxParagraphsPerNode, int chatRequestPerMinute) {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data enrichment...");
        System.out.println("  - maxParagraphsPerNode: " + maxParagraphsPerNode);
        System.out.println("  - chatRequestPerMinute: " + chatRequestPerMinute);

        RateLimiter rateLimiter = RateLimiter.create(chatRequestPerMinute / 60.0);

        dataStore.findUnprocessedClassNodes().stream().forEach(classNode -> {
            classNode.setProcessed(true);
            classNode = bindNodeWithParagraphs(classNode, maxParagraphsPerNode);
            classNode = enrichNodeWithSummary(classNode);
            dataStore.save(classNode);
            rateLimiter.acquire();
        });

        long endTime = System.currentTimeMillis();
        System.out.println("+ Enrichment completed in " + (endTime - startTime) + "ms");
    }

    private ClassNode bindNodeWithParagraphs(ClassNode classNode, int maxParagraphsPerNode) {
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

        String rawResponse = response.getResult().getOutput().getText();
        ClassNodeSummary nodeSummary;
        try {
            nodeSummary = outputConverter.convert(rawResponse);
        } catch (Exception e) {
            System.err.println("Output conversion failed. Store raw output to summary. Error: "
                    + e.getMessage());
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
                    "prompt", prompt.toString(),
                    "description", nodeSummary.description(),
                    "exampleUsage", nodeSummary.usage(),
                    "relationship", nodeSummary.relationship());
            JsonBackupUtils.appendJsonBackup(transformChatHistoryFile, entry);
        } catch (IOException e) {
            System.err.println("Failed to save history: " + e.getMessage());
        }

        return classNode;
    }

    public void chunk(TextSplitter splitter) {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data chunking...");

        // chunk all class nodes
        dataStore.findProcessedClassNodes().stream().forEach(node -> {
            List<Document> docs = new ArrayList<>();
            docs.add(new Document(cleanText(node.getCodeText()),
                    Map.of("type", ChunkType.CODE, "source_id", node.getId())));
            docs.add(new Document(cleanText(node.getDescriptionText()),
                    Map.of("type", ChunkType.SUMMARY, "source_id", node.getId())));
            docs.add(new Document(cleanText(node.getUsageText()),
                    Map.of("type", ChunkType.SUMMARY, "source_id", node.getId())));
            docs.add(new Document(cleanText(node.getRelationshipText()),
                    Map.of("type", ChunkType.SUMMARY, "source_id", node.getId())));

            List<Document> splitDocs = splitter.split(docs);
            saveAllDocumentsToChunks(dataStore, splitDocs);
            System.out.println("Created " + splitDocs.size() + " chunks for ClassNode: "
                    + node.getSignature());
        });

        // chunk all document paragraphs
        dataStore.findAllDocumentParagraphs().stream().forEach(paragraph -> {
            Document doc = new Document(cleanText(paragraph.getContent()),
                    Map.of("type", ChunkType.PARAGRAPH, "source_id", paragraph.getId()));
            List<Document> splitDocs = splitter.split(doc);
            saveAllDocumentsToChunks(dataStore, splitDocs);
            System.out
                    .println("Created " + splitDocs.size() + " chunks for DocumentParagraph ID: "
                            + paragraph.getId());
        });

        long endTime = System.currentTimeMillis();
        System.out.println("+ Chunking completed in " + (endTime - startTime) + "ms");
    }

    private void saveAllDocumentsToChunks(DataStore dataStore, List<Document> documents) {
        List<Chunk> chunks = documents.stream()
                .map(d -> new Chunk(d))
                .collect(Collectors.toList());
        dataStore.saveAllChunks(chunks);
    }

    private String cleanText(String text) {
        return text
                .replaceAll("\\\\u[0-9a-fA-F]{4}", "<UNICODE>") // replace unicode sequences
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", ""); // remove control characters except newlines and tabs
    }
}
