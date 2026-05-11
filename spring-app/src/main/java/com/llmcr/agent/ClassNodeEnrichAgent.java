package com.llmcr.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import com.llmcr.client.LargeChatClient;
import com.llmcr.rag.retrieval.QueryContextRetriever;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextScorePair;
import com.llmcr.rag.retrieval.select.AdaptiveKStrategy;

@Component
public class ClassNodeEnrichAgent {

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

    private final ChatClient chatClient;
    private final QueryContextRetriever queryContextRetriever;
    private final BeanOutputConverter<ClassNodeEnrichOutput> outputConverter;

    public ClassNodeEnrichAgent(LargeChatClient chatClient, QueryContextRetriever queryContextRetriever) {
        this.chatClient = chatClient.getChatClient();
        this.queryContextRetriever = queryContextRetriever;
        this.outputConverter = new BeanOutputConverter<>(ClassNodeEnrichOutput.class);
    }

    public ClassNodeEnrichOutput execute(ClassNodeEnrichInput input) {
        String contextText = retrieveContext(input);

        String userMessage = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(USER_MESSAGE_TEMPLATE)
                .build()
                .render(Map.of("class_content", input.classContent()));

        String contextMessage = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(CONTEXT_MESSAGE_TEMPLATE)
                .build()
                .render(Map.of("context", contextText));

        String fullPrompt = SYSTEM_MESSAGE + "\n\n" + userMessage + "\n\n" + contextMessage + "\n\n" +
                outputConverter.getFormat();

        ResponseEntity<ChatResponse, ClassNodeEnrichOutput> response = chatClient
                .prompt(fullPrompt)
                .call()
                .responseEntity(ClassNodeEnrichOutput.class);

        return response.entity();
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
