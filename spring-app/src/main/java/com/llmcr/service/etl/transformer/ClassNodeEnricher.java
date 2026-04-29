package com.llmcr.service.etl.transformer;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import com.llmcr.advisor.RAGAdvisor;
import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.model.LargeChatClient;
import com.llmcr.service.rag.ContextRetriever.RetrievalConfiguration;
import com.llmcr.service.rag.select.FixedKStrategy;

/**
 * Enrich ClassNode context by generating a summary using LLM.
 */
@Component
public class ClassNodeEnricher implements ContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(ClassNodeEnricher.class);
    private static final int QUERY_CHUNK_SIZE = 1200;

    private static final String ENRICHMENT_PROMPT_TEMPLATE = """
            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and its related documentation contents.
            You should generate below information for enrichment:
            - **functional**: What does this class do?
            - **relationship**: How does this class relate to other classes or components in the project?
            - **usage**: A example that show the most important usage scenario of this class, illustrate the one most important example in natural language rather than code.

            Raw code at below.
            ```java
            <user_input>
            ```

            Documentation contents at below.
            -----------------
            <contexts>
            -----------------

            Output the enrichment in the following JSON format:
            %s

            Enrichment:
            """;

    private static final String QUERY_DESCRIPTION = """
            Retrieve documentation that helps explain the behavior, relationship, and usage of this Java class fragment.
            Focus on API intent, collaborator relationship, and concrete usage clues.
            """;

    private static final RetrievalConfiguration RETRIEVAL_CONFIGURATION = new RetrievalConfiguration(
            5,
            "DOCUMENT",
            false,
            new FixedKStrategy());

    private static final BeanOutputConverter<ClassNodeEnrichment> outputConverter = new BeanOutputConverter<>(
            ClassNodeEnrichment.class);

    private record ClassNodeEnrichment(String functional, String relationship, String usage) {
    }

    private final LargeChatClient chatModel;
    private final RAGAdvisor ragAdvisor;

    public ClassNodeEnricher(LargeChatClient chatModel, RAGAdvisor ragAdvisor) {
        this.chatModel = chatModel;
        this.ragAdvisor = ragAdvisor;
    }

    @Override
    public boolean supports(Context context) {
        return context.getType() == Context.ContextType.CLASSNODE;
    }

    @Override
    public Context apply(Context classNode) {
        String ragPromptTemplate = ENRICHMENT_PROMPT_TEMPLATE.formatted(outputConverter.getFormat());
        List<String> retrievalQueries = buildClassNodeQueries(classNode.getContent());

        ChatResponse response = chatModel.getChatClient().prompt()
                .advisors(spec -> spec
                        .advisors(ragAdvisor)
                        .param(RAGAdvisor.RETRIEVAL_CONFIGURATION_PARAM, RETRIEVAL_CONFIGURATION)
                        .param(RAGAdvisor.QUERY_LIST_PARAM, retrievalQueries)
                        .param(RAGAdvisor.PROMPT_TEMPLATE_PARAM, ragPromptTemplate))
                .user(classNode.getContent())
                .call()
                .chatResponse();

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

    private List<String> buildClassNodeQueries(String classNodeContent) {
        List<String> chunks = splitIntoChunks(classNodeContent, QUERY_CHUNK_SIZE);
        List<String> queries = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String query = """
                    %s

                    Chunk %d/%d:
                    %s
                    """.formatted(QUERY_DESCRIPTION, i + 1, chunks.size(), chunk);
            queries.add(query);
        }

        return queries;
    }

    private List<String> splitIntoChunks(String content, int chunkSize) {
        if (content == null || content.isBlank()) {
            return List.of("");
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            chunks.add(content.substring(start, end));
            start = end;
        }

        return chunks;
    }
}
