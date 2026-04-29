package com.llmcr.service.etl.transformer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.model.LargeChatClient;
import com.llmcr.service.rag.retrieval.ContextRetriever;
import com.llmcr.service.rag.retrieval.ContextRetriever.ContextScorePair;
import com.llmcr.service.rag.retrieval.ContextRetriever.RetrievalConfiguration;
import com.llmcr.service.rag.retrieval.select.AdaptiveKStrategy;

/**
 * Enrich ClassNode context by generating a summary using LLM.
 */
@Component
public class ClassNodeEnricher implements ContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(ClassNodeEnricher.class);

    private static final PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template(
                    """
                            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and its related documentation contents.
                            You should generate below information for enrichment:
                            - **functional**: What does this class do?
                            - **relationship**: How does this class relate to other classes or components in the project?
                            - **usage**: A example that show the most important usage scenario of this class, illustrate the one most important example in natural language rather than code.

                            Raw code at below.
                            ```java
                            <code>
                            ```

                            Documentation contents at below.
                            -----------------
                            <doc>
                            -----------------

                            Output the enrichment in the following JSON format:
                            <format>

                            Enrichment:
                            """)
            .build();
    private static final BeanOutputConverter<ClassNodeEnrichment> outputConverter = new BeanOutputConverter<>(
            ClassNodeEnrichment.class);

    private record ClassNodeEnrichment(String functional, String relationship, String usage) {
    }

    private final LargeChatClient chatModel;
    private final ContextRetriever contextRetriever;

    public ClassNodeEnricher(LargeChatClient chatModel, ContextRetriever contextRetriever) {
        this.chatModel = chatModel;
        this.contextRetriever = contextRetriever;
    }

    @Override
    public boolean supports(Context context) {
        return context.getType() == Context.ContextType.CLASSNODE;
    }

    @Override
    public Context apply(Context classNode) {
        // retrieve relevant document contexts for enrichment
        List<ContextScorePair> relevantDocuments = contextRetriever.retrieve(classNode.getContent(),
                new RetrievalConfiguration(5, "DOCUMENT", false, new AdaptiveKStrategy()));

        log.info("Enriching class node {} with documents: {}", classNode.getName(), relevantDocuments.stream()
                .map(p -> p.context().getId() + "(score=" + p.score() + ")")
                .collect(Collectors.joining(", ")));

        // build prompt
        String doc = relevantDocuments.stream()
                .map(c -> c.context().getContent())
                .collect(Collectors.joining("\n-----------------\n"));

        Prompt prompt = promptTemplate.create(Map.of(
                "code", classNode.getContent(),
                "doc", doc,
                "format", outputConverter.getFormat()));

        log.info("Enrichment prompt for class node {}: {}", classNode.getId(), prompt.getContents());

        // call chat model
        ChatResponse response;
        response = chatModel.call(prompt);
        String rawResponse = response.getResult().getOutput().getText();

        log.info("Raw enrichment response for class node {}: {}", classNode.getId(), rawResponse);

        ClassNodeEnrichment enrichment;
        try {
            enrichment = outputConverter.convert(rawResponse);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to convert chat response to ClassNodeEnrichment. Response: "
                            + rawResponse,
                    e);
        }

        // update class node
        classNode.addChunk(new Chunk(enrichment.functional()));
        classNode.addChunk(new Chunk(enrichment.relationship()));
        classNode.addChunk(new Chunk(enrichment.usage()));

        return classNode;
    }
}
