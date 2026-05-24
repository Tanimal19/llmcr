package com.llmcr.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Service;

import com.llmcr.api.APIServiceException;
import com.llmcr.api.APIServiceException.ErrorCode;
import com.llmcr.agent.logging.AgentLoggerAdvisor;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.service.etl.LoadService;
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

    public static final String AGENT_NAME = "question-answer";
    public static final String COLLECTION_NAME = "question-answer";

    private final ApplicationProperties applicationProperties;
    private final ModelClientFactory modelClientFactory;
    private final ChunkCollectionRepository chunkCollectionRepository;
    private final TrackRootRepository trackRootRepository;
    private final LoadService loadService;
    private final QueryContextRetriever queryContextRetriever;

    private final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION;

    public ChatService(
            ApplicationProperties applicationProperties,
            ModelClientFactory modelClientFactory,
            ChunkCollectionRepository chunkCollectionRepository,
            TrackRootRepository trackRootRepository,
            LoadService loadService,
            QueryContextRetriever queryContextRetriever) {
        this.applicationProperties = applicationProperties;
        this.modelClientFactory = modelClientFactory;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.trackRootRepository = trackRootRepository;
        this.loadService = loadService;

        this.RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
                10,
                new AdaptiveKStrategy(),
                COLLECTION_NAME,
                false);
        this.queryContextRetriever = queryContextRetriever;
    }

    public record ChatResponse(String answer, Map<String, Float> retrievedContexts) {
    }

    public ChatResponse chat(String query) {
        if (query == null || query.isBlank()) {
            return new ChatResponse("(no query provided)", Map.of());
        }

        List<ContextScorePair> retrievedContexts = queryContextRetriever
                .retrieve(new ContextRetrievalRequest(List.of(query), RETRIEVAL_CONFIGURATION));

        String contextString = String.join("\n---\n", retrievedContexts.stream()
                .map(pair -> pair.context().getContent())
                .toList());

        String userMessage = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(USER_MESSAGE_TEMPLATE)
                .build()
                .render(Map.of("query", query, "context", contextString));

        ChatClient chatClient = modelClientFactory.createChatClient(
                applicationProperties.getAgents().get(AGENT_NAME).getChatModelProperties().getProvider(),
                applicationProperties.getAgents().get(AGENT_NAME).getChatModelProperties().getName());
        ChatClientRequestSpec requestSpec = chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .advisors(new AgentLoggerAdvisor(this.getClass().getSimpleName()));

        String answer = requestSpec.call().content();
        Map<String, Float> retrievedContextMap = retrievedContexts.stream()
                .collect(Collectors.toMap(pair -> pair.context().getName(), ContextScorePair::score));
        return new ChatResponse(answer, retrievedContextMap);
    }

    /**
     * Return all track root paths with true/false indicating whether they are
     * included in the RAG scope.
     */
    public Map<String, Boolean> getRagScope() {
        try
        {
            List<String> allTrackRoots = trackRootRepository.findAll().stream()
                    .map(TrackRoot::getPath)
                    .toList();
            ChunkCollection collection = chunkCollectionRepository.findByName(COLLECTION_NAME).orElse(null);
            if (collection == null) {
                return allTrackRoots.stream().collect(Collectors.toMap(path -> path, path -> false));
            }
            Set<String> includedTrackRootPaths = collection.getTrackRoots().stream()
                    .map(TrackRoot::getPath)
                    .collect(Collectors.toSet());
            return allTrackRoots.stream().collect(Collectors.toMap(path -> path, includedTrackRootPaths::contains));
        }
        catch(Exception ex)
        {
            throw new APIServiceException(ErrorCode.RAG_SCOPE_GET_FAILED, "Failed to get RAG scope", ex);
        }
    }

    public void setRagScope(Set<String> trackRootPaths) {
        try
        {
            Set<TrackRoot> trackRoots = new HashSet<>(trackRootRepository.findByPaths(trackRootPaths));
            ChunkCollection collection = chunkCollectionRepository.findByName(COLLECTION_NAME).orElse(null);
            if (collection == null) {
                collection = new ChunkCollection(COLLECTION_NAME, trackRoots);
                collection.setName(COLLECTION_NAME);
            } else {
                collection.clearTrackRoots();
                collection.addTrackRoots(trackRoots);
            }
            chunkCollectionRepository.save(collection);
            loadService.reloadCollection(COLLECTION_NAME);
        }
        catch(Exception ex)
        {
            throw new APIServiceException(ErrorCode.RAG_SCOPE_SET_FAILED, "Faild to set RAG scope", ex);
        }
    }
}
