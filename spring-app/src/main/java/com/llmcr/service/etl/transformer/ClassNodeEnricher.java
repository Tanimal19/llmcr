package com.llmcr.service.etl.transformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.agent.Agent;
import com.llmcr.agent.AgentInput;
import com.llmcr.client.ChatClientWrapper;
import com.llmcr.client.LargeChatClient;
import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.service.rag.ContextAugmentAdvisor;
import com.llmcr.service.rag.RAGInput;
import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.retrieval.select.AdaptiveKStrategy;

/**
 * Enrich ClassNode context by generating a summary using LLM.
 */
@Component
public class ClassNodeEnricher implements ContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(ClassNodeEnricher.class);

    /**
     * Content length below this threshold is unlikely to benefit from enrichment,
     * so we skip to save cost
     */
    private static final int MIN_CONTENT_LENGTH = 1000;

    /**
     * Exclude class names that are likely to be test or configuration classes,
     * which often have less clear functional description and more noisy
     * relationships and usage. This is a heuristic to further reduce unnecessary
     * enrichment cost.
     */
    private static final List<String> CLASSNAME_EXCLUDE = List.of("Test", "Configuration", "Properties");

    private final ClassNodeEnrichAgent classNodeEnrichAgent;

    public ClassNodeEnricher(LargeChatClient chatModel, ContextAugmentAdvisor.Builder ragAdvisorBuilder) {
        this.classNodeEnrichAgent = new ClassNodeEnrichAgent(chatModel, ragAdvisorBuilder);
    }

    @Override
    public boolean supports(Context context) {
        return context.getType() == Context.ContextType.CLASSNODE;
    }

    @Override
    public Context apply(Context classNode) {
        if (classNode.getContent().length() < MIN_CONTENT_LENGTH) {
            log.info("Class node content is short ({}), skip enrichment", classNode.getContent().length());
            return classNode;
        }
        for (String exclude : CLASSNAME_EXCLUDE) {
            if (classNode.getName().contains(exclude)) {
                log.info("Class node name contains '{}', skip enrichment", exclude);
                return classNode;
            }
        }

        ClassNodeEnrichOutput enrichment = classNodeEnrichAgent
                .execute(new ClassNodeEnrichInput(classNode.getContent()));

        // update class node
        classNode.addChunk(new Chunk(enrichment.functional()));
        classNode.addChunk(new Chunk(enrichment.relationship()));
        classNode.addChunk(new Chunk(enrichment.usage()));

        return classNode;
    }

    private record ClassNodeEnrichInput(String classContent) implements AgentInput, RAGInput {

        private static final int QUERY_CHUNK_SIZE = 2000;

        @Override
        public Map<String, Object> getTemplateVariables() {
            return Map.of("class_content", classContent);
        }

        @Override
        public List<String> buildQueries() {
            List<String> queries = new ArrayList<>();
            int start = 0;
            while (start < classContent.length()) {
                int end = Math.min(start + QUERY_CHUNK_SIZE, classContent.length());
                queries.add(classContent.substring(start, end));
                start = end;
            }
            return queries;
        }
    }

    private record ClassNodeEnrichOutput(String functional, String relationship, String usage) {
    }

    private class ClassNodeEnrichAgent extends Agent<ClassNodeEnrichInput, ClassNodeEnrichOutput> {

        private static final String SYSTEM_MESSAGE = """
                You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and its related documentation contents.
                You should generate below information for enrichment:
                - **functional**: What does this class do?
                - **relationship**: How does this class relate to other classes or components in the project?
                - **usage**: A example that show the most important usage scenario of this class, illustrate the one most important example in natural language rather than code.

                Do not make assumptions beyond the provided code and documentation.
                """;

        private static final String USER_MESSAGE_TEMPLATE = """
                Raw code at below.
                ```java
                <class_content>
                ```
                """;

        private static final String CONTEXT_MESSAGE_TEMPLATE = """
                Documentation contents at below.
                -----------------
                <context>
                -----------------
                """;

        private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
                10,
                new AdaptiveKStrategy(),
                "project-context",
                false);

        private final LargeChatClient chatClient;

        private ClassNodeEnrichAgent(LargeChatClient chatClient, ContextAugmentAdvisor.Builder ragAdvisorBuilder) {
            this.chatClient = chatClient;
            super.advisors.add(ragAdvisorBuilder
                    .retrievalConfiguration(RETRIEVAL_CONFIGURATION)
                    .messageTemplate(CONTEXT_MESSAGE_TEMPLATE)
                    .build());
        }

        @Override
        public ChatClientWrapper chatClient() {
            return chatClient;
        }

        @Override
        public String systemMessage() {
            return SYSTEM_MESSAGE;
        }

        @Override
        public String userMessageTemplate() {
            return USER_MESSAGE_TEMPLATE;
        }

        @Override
        public Class<ClassNodeEnrichOutput> outputClass() {
            return ClassNodeEnrichOutput.class;
        }

        @Override
        protected void preprocess(ClassNodeEnrichInput input) {
            super.advisorParams.put(ContextAugmentAdvisor.RAG_INPUT, input);
        }
    }

}
