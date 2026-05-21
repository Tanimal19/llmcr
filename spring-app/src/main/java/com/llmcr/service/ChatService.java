package com.llmcr.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Service;

import com.llmcr.agent.logging.AgentLoggerAdvisor;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.config.ConfigReader;
import com.llmcr.service.rag.QueryContextRetriever;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.AdaptiveKStrategy;

@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            You are a software engineering assistant.

            Your task is to answer the user's query using the provided project context.
            Do not make any assumptions or use any information that is not included in the provided context, even if it seems obvious to you as a software engineer. If the answer cannot be found in the provided context, say you don't know instead of trying to infer or guess.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            User query:
            <query>

            Retrieved project context:
            <context>

            Answer:
            """;

    /**
     * @param providerName   The name of the model provider to use.
     * @param modelName      The name of the model to use.
     * @param TrackRootNames The set of track root names that define the scope of
     *                       the context to retrieve for this chat service.
     */
    public record ChatServiceConfiguration(String providerName, String modelName, Set<String> TrackRootNames) {
    }

    public static final String AGENT_NAME = "question-answer";
    public static final String COLLECTION_NAME = "question-answer";

    private final ApplicationProperties applicationProperties;
    private final ModelClientFactory modelClientFactory;
    private final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION;
    private final QueryContextRetriever QUERY_CONTEXT_RETRIEVER;

    public ChatService(
            ApplicationProperties applicationProperties,
            ConfigReader configReader,
            ModelClientFactory modelClientFactory,
            QueryContextRetriever queryContextRetriever) {
        this.applicationProperties = applicationProperties;
        this.modelClientFactory = modelClientFactory;
        this.RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
                10,
                new AdaptiveKStrategy(),
                COLLECTION_NAME,
                false);
        this.QUERY_CONTEXT_RETRIEVER = queryContextRetriever;
    }

    public String chat(String query) {
        String userMessage = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(USER_MESSAGE_TEMPLATE)
                .build()
                .render(Map.of("query", query, "context", retrieveContext(query)));

        ChatClient chatClient = modelClientFactory.createChatClient(
                applicationProperties.getAgents().get(AGENT_NAME).getChatModelProperties().getProvider(),
                applicationProperties.getAgents().get(AGENT_NAME).getChatModelProperties().getName());
        ChatClientRequestSpec requestSpec = chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .advisors(new AgentLoggerAdvisor(this.getClass().getSimpleName()));

        return requestSpec.call().content();
    }

    private String retrieveContext(String query) {
        if (query == null || query.isBlank()) {
            return "(no query provided)";
        }

        List<ContextScorePair> retrievedContexts = QUERY_CONTEXT_RETRIEVER
                .retrieve(new ContextRetrievalRequest(List.of(query), RETRIEVAL_CONFIGURATION));

        if (retrievedContexts.isEmpty()) {
            return "(no relevant context retrieved)";
        }

        return String.join("\n---\n", retrievedContexts.stream()
                .map(pair -> pair.context().getContent())
                .toList());
    }

}
