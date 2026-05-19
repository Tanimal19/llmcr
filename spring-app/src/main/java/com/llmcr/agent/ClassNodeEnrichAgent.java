package com.llmcr.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.SingleCallAgent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.service.rag.QueryContextRetriever;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.AdaptiveKStrategy;

@Component
public class ClassNodeEnrichAgent extends
        SingleCallAgent<ClassNodeEnrichAgent.ClassNodeEnrichInput, ClassNodeEnrichAgent.ClassNodeEnrichOutput> {

    public record ClassNodeEnrichInput(String classContent) {
        private static final int QUERY_CHUNK_SIZE = 2000;

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

    public record ClassNodeEnrichOutput(String functional, String relationship, String usage) {
    }

    private static final String PROMPT_TEMPLATE = """
            You are a knowledgeable java engineer. Your task is to generate a concise and clear summary for the given data: raw code of a Java class, and its related documentation contents.
            You should generate below information for enrichment:
            - **functional**: What does this class do?
            - **relationship**: How does this class relate to other classes or components in the project?
            - **usage**: A example that show the most important usage scenario of this class, illustrate the one most important example in natural language rather than code.

            Do not make assumptions beyond the provided code and documentation.

            Raw code at below.
            ```java
            <class_content>
            ```

            Documentation contents at below.
            -----------------
            <context>
            -----------------

            <format_instructions>
            """;

    private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
            10,
            new AdaptiveKStrategy(),
            "project-context",
            false);

    private final QueryContextRetriever queryContextRetriever;

    public ClassNodeEnrichAgent(
            @Value("${llmcr.agent.classNodeEnrich.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.classNodeEnrich.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            QueryContextRetriever queryContextRetriever) {
        super(chatProviderName, chatModelName, modelClientFactory,
                new BeanOutputConverter<>(ClassNodeEnrichOutput.class));
        this.queryContextRetriever = queryContextRetriever;
    }

    @Override
    protected String buildInitialMessageTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    protected Map<String, Object> buildInputVariables(ClassNodeEnrichInput input) {
        String contextText = retrieveContext(input);
        return Map.of(
                "class_content", input.classContent(),
                "context", contextText);
    }

    private String retrieveContext(ClassNodeEnrichInput input) {
        List<String> queries = input.buildQueries();
        List<ContextScorePair> retrievedContexts = queryContextRetriever
                .retrieve(new ContextRetrievalRequest(queries, RETRIEVAL_CONFIGURATION));
        return String.join("\n---\n", retrievedContexts.stream()
                .map(pair -> pair.context().getContent())
                .toList());
    }
}
