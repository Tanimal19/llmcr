package com.example.llmcr.service.etl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.transformer.splitter.TextSplitter;

import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.entity.Embedding.EmbeddingContentType;
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

        private RateLimiter rateLimiter = RateLimiter.create(2.0 / 60.0);

        private static final Logger LOGGER = LoggerFactory.getLogger(TransformService.class);

        private record ClassNodeSummary(
                        String description,
                        String usage,
                        String relationship) {
        }

        public TransformService(DataStore dataStore, ChatModel chatModel) {
                this.dataStore = dataStore;
                this.chatModel = chatModel;
        }

        public void enrich(int maxParagraphsPerNode) {
                long startTime = System.currentTimeMillis();
                LOGGER.info("Start data enrichment");

                dataStore.findUnprocessedClassNodes().stream().forEach(classNode -> {
                        classNode = bindNodeWithParagraphs(classNode, maxParagraphsPerNode);
                        classNode = enrichNodeWithSummary(classNode);
                        classNode.setProcessed(true);
                        dataStore.save(classNode);
                });

                long endTime = System.currentTimeMillis();
                LOGGER.info("Data enrichment completed in " + (endTime - startTime) + "ms");
        }

        private ClassNode bindNodeWithParagraphs(ClassNode classNode, int maxParagraphsPerNode) {
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
                return classNode;
        }

        private ClassNode enrichNodeWithSummary(ClassNode classNode) {
                String formatInstruction = outputConverter.getFormat();
                Map<String, Object> variables = Map.of(
                                "code", classNode.getContent(),
                                "doc", classNode.getDocumentParagraphs().stream()
                                                .map(DocumentParagraph::getContent)
                                                .collect(Collectors.joining("\n")),
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
                                        "prompt", prompt.toString(),
                                        "description", nodeSummary.description(),
                                        "exampleUsage", nodeSummary.usage(),
                                        "relationship", nodeSummary.relationship());
                        JsonBackupUtils.appendJsonBackup(transformChatHistoryFile, entry);
                } catch (IOException e) {
                        LOGGER.warn("Failed to save transform history for ClassNode: "
                                        + classNode.getSignature()
                                        + ". Error: " + e.getMessage());
                }

                LOGGER.info("Enriched ClassNode: " + classNode.getSignature());

                return classNode;
        }

        public void chunk(TextSplitter splitter) {
                long startTime = System.currentTimeMillis();
                LOGGER.info("Start data chunking");

                // embeddings all class nodes
                dataStore.findProcessedClassNodes().stream().forEach(node -> {
                        List<Document> docs = new ArrayList<>();

                        docs.add(new Document(textFilter(node.getContent()),
                                        Map.of("content_type", EmbeddingContentType.CODE, "source", node)));

                        docs.add(new Document(textFilter(node.getDescriptionText()),
                                        Map.of("content_type", EmbeddingContentType.ENRICHMENT, "source", node)));
                        docs.add(new Document(textFilter(node.getUsageText()),
                                        Map.of("content_type", EmbeddingContentType.ENRICHMENT, "source", node)));
                        docs.add(new Document(textFilter(node.getRelationshipText()),
                                        Map.of("content_type", EmbeddingContentType.ENRICHMENT, "source", node)));

                        List<Document> splitDocs = splitter.split(docs);
                        dataStore.saveAllEmbeddingsByDocuments(splitDocs);
                        LOGGER.info("Created " + splitDocs.size() + " chunks for ClassNode: "
                                        + node.getSignature());
                });

                // chunk all document paragraphs
                dataStore.findAllDocumentParagraphs().stream().forEach(paragraph -> {
                        Document doc = new Document(textFilter(paragraph.getContent()),
                                        Map.of("content_type", EmbeddingContentType.DOCUMENT, "source", paragraph));
                        List<Document> splitDocs = splitter.split(doc);
                        dataStore.saveAllEmbeddingsByDocuments(splitDocs);
                        LOGGER.info("Created " + splitDocs.size() + " chunks for DocumentParagraph: "
                                        + paragraph.getId());
                });

                long endTime = System.currentTimeMillis();
                LOGGER.info("Data chunking completed in " + (endTime - startTime) + "ms");
        }

        private String textFilter(String text) {
                return text
                                // remove control characters except newlines nd tabs
                                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                                // specific cleaning for ANTLR serialized ATN
                                .replaceAll("_serializedATN\\s*=\\s*\"[\\s\\S]*?\";",
                                                "_serializedATN = \"<ANTLR_SERIALIZED_ATN>\";");
        }
}
