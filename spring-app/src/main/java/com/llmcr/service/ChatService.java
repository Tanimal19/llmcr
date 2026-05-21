package com.llmcr.service;

import java.util.HashSet;
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
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.TrackRootRepository;
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
    private final ChunkCollectionRepository chunkCollectionRepository;
    private final TrackRootRepository trackRootRepository;
    private final QueryContextRetriever queryContextRetriever;

    private final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION;

    public ChatService(
            ApplicationProperties applicationProperties,
            ModelClientFactory modelClientFactory,
            ChunkCollectionRepository chunkCollectionRepository,
            TrackRootRepository trackRootRepository,
            QueryContextRetriever queryContextRetriever) {
        this.applicationProperties = applicationProperties;
        this.modelClientFactory = modelClientFactory;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.trackRootRepository = trackRootRepository;

        this.RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
                10,
                new AdaptiveKStrategy(),
                COLLECTION_NAME,
                false);
        this.queryContextRetriever = queryContextRetriever;
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

        List<ContextScorePair> retrievedContexts = queryContextRetriever
                .retrieve(new ContextRetrievalRequest(List.of(query), RETRIEVAL_CONFIGURATION));

        if (retrievedContexts.isEmpty()) {
            return "(no relevant context retrieved)";
        }

        return String.join("\n---\n", retrievedContexts.stream()
                .map(pair -> pair.context().getContent())
                .toList());
    }

    public Set<String> getRagScope() {
        ChunkCollection collection = chunkCollectionRepository.findByName(COLLECTION_NAME).orElse(null);
        if (collection == null) {
            return new HashSet<>();
        }
        return collection.getTrackRoots().stream().map(TrackRoot::getPath).collect(java.util.stream.Collectors.toSet());
    }

    public void setRagScope(Set<String> trackRootPaths) {
        Set<TrackRoot> trackRoots = new HashSet<>(trackRootRepository.findByPaths(trackRootPaths));
        ChunkCollection collection = chunkCollectionRepository.findByName(COLLECTION_NAME).orElse(null);
        if (collection == null) {
            collection = new ChunkCollection(COLLECTION_NAME, trackRoots);
            collection.setName(COLLECTION_NAME);
        } else {
            collection.clearTrackRoots();
            collection.addTrackRoots(trackRoots);
        }
    }
}
