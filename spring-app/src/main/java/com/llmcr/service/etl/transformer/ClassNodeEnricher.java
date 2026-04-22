package com.llmcr.service.etl.transformer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.faiss.FaissVectorStore;
import com.llmcr.service.faiss.FaissVectorStore.ContextHolder;

public class ClassNodeEnricher implements ContextTransformer {

    private static final PromptTemplate enrichmentPromptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template(
                    """
                            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and its related documentation contents.
                            You should generate below information:
                            - **functional**: What does this class do?
                            - **relationship**: How does this class relate to other classes or components in the project?
                            - **usage**: A example that show the most important usage scenario of this class, illustrate the one most important example in natural language rather than code.

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
    private static final BeanOutputConverter<ClassNodeEnrichment> outputConverter = new BeanOutputConverter<>(
            ClassNodeEnrichment.class);

    private record ClassNodeEnrichment(String functional, String relationship, String usage) {
    }

    private final FaissVectorStore faissVectorStore;

    public ClassNodeEnricher(FaissVectorStore faissVectorStore) {
        this.faissVectorStore = faissVectorStore;
    }

    @Override
    public boolean supports(Context context) {
        return context.getType() == Context.ContextType.CLASSNODE;
    }

    @Override
    public List<Chunk> apply(Context context) {
        assert context.getType() == Context.ContextType.CLASSNODE;

        

        List<ContextHolder> contextHolders = faissVectorStore.similaritySearch(new SearchRequest(), "PROJECT_CONTEXT");

            // enrich node with llm generated summary
            // build prompt
            String code = Objects.toString(classNode.getContent(), "");
            List<DocumentContext> paragraphs = classNode.getDocumentParagraphs();
            String doc = paragraphs == null ? ""
                    : paragraphs.stream()
                            .map(DocumentContext::getContent)
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
                    logger.warn("Chat model call failed: " + e.getMessage());
                }

                count++;
                logger.warn("Retry: attempt #" + count);
                if (count > 5) {
                    throw new RuntimeException("Failed to call chat model.");
                }
            }

            String rawResponse = response.getResult().getOutput().getText();
            ClassNodeSummary nodeSummary;
            try {
                nodeSummary = outputConverter.convert(rawResponse);
            } catch (Exception e) {
                logger.warn("Output conversion failed for ClassNode: "
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
                logger.warn("Failed to save transform history for ClassNode: "
                        + classNode.getSignature()
                        + ". Error: " + e.getMessage());
            }

            logger.info("Enriched ClassNode: " + classNode.getSignature());

            classNode.setProcessed(true);
            dataStore.save(classNode);
        });

    long endTime = System.currentTimeMillis();logger.info("Data enrichment completed in "+(endTime-startTime)+"ms");
}}
