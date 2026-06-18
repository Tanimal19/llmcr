package com.llmcr.feature.chat;

import com.llmcr.config.SystemConfig.ModelConfig;
import com.llmcr.config.provider.ChatServiceConfigProvider;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.exception.APIServiceException.ErrorCode;
import com.llmcr.domain.repository.ContextRepository;
import com.llmcr.domain.repository.TrackRootRepository;
import com.llmcr.infrastructure.agent.logging.AgentLoggerAdvisor;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import com.llmcr.infrastructure.rag.ContextScorePair;
import com.llmcr.infrastructure.rag.QueryContextRetriever;
import com.llmcr.infrastructure.rag.QueryContextRetriever.QueryContextRetrievalConfig;
import com.llmcr.infrastructure.rag.QueryContextRetriever.QueryContextRetrievalRequest;
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

@Service
public class ChatService {

  private static final String SYSTEM_PROMPT =
      """
            You are a software engineering assistant.

            Your task is to answer the user's query using the provided project context.
            Do not make any assumptions or use any information that is not included in the provided context, even if it seems obvious to you as a software engineer. If the answer cannot be found in the provided context, say you don't know instead of trying to infer or guess.
            """;

  private static final String USER_MESSAGE_TEMPLATE =
      """
            User query:
            <query>

            Retrieved project context:
            <context>

            Answer:
            """;

  public static final int RETRIEVAL_TOP_K = 10;

  private final ModelConfig chatModelConfig;
  private final ModelClientFactory modelClientFactory;
  private final TrackRootRepository trackRootRepository;
  private final ContextRepository contextRepository;
  private final QueryContextRetriever queryContextRetriever;

  /** Track root paths in scope for RAG. Null means all track roots are in scope. */
  private Set<String> scopedTrackRootPaths = null;

  public ChatService(
      ChatServiceConfigProvider configProvider,
      ModelClientFactory modelClientFactory,
      TrackRootRepository trackRootRepository,
      ContextRepository contextRepository,
      QueryContextRetriever queryContextRetriever) {
    this.chatModelConfig = configProvider.getChatServiceModelConfig();
    this.modelClientFactory = modelClientFactory;
    this.trackRootRepository = trackRootRepository;
    this.contextRepository = contextRepository;
    this.queryContextRetriever = queryContextRetriever;
  }

  public record ChatResponse(String answer, Map<String, Float> retrievedContexts) {}

  public ChatResponse chat(String query) {
    Set<Long> contextIds = resolveContextIds();
    QueryContextRetrievalConfig config = new QueryContextRetrievalConfig(contextIds, RETRIEVAL_TOP_K);

    List<ContextScorePair> retrievedContexts;
    try {
      retrievedContexts =
          queryContextRetriever.retrieve(new QueryContextRetrievalRequest(List.of(query), config));
    } catch (Exception ex) {
      throw new APIServiceException(ErrorCode.RAG_RETRIEVAL_FAILED, ex);
    }

    String contextString =
        String.join(
            "\n---\n",
            retrievedContexts.stream().map(pair -> pair.context().getContent()).toList());

    String userMessage =
        PromptTemplate.builder()
            .renderer(
                StTemplateRenderer.builder()
                    .startDelimiterToken('<')
                    .endDelimiterToken('>')
                    .build())
            .template(USER_MESSAGE_TEMPLATE)
            .build()
            .render(Map.of("query", query, "context", contextString));

    ChatClient chatClient = modelClientFactory.createChatClient(chatModelConfig);
    ChatClientRequestSpec requestSpec =
        chatClient
            .prompt()
            .system(SYSTEM_PROMPT)
            .user(userMessage)
            .advisors(new AgentLoggerAdvisor(this.getClass().getSimpleName()));

    String answer;
    try {
      answer = requestSpec.call().content();
    } catch (Exception ex) {
      throw new APIServiceException(ErrorCode.CHAT_MODEL_RESPONSE_FAILED, ex);
    }

    Map<String, Float> retrievedContextMap =
        retrievedContexts.stream()
            .collect(Collectors.toMap(pair -> pair.context().getName(), ContextScorePair::score));
    return new ChatResponse(answer, retrievedContextMap);
  }

  /**
   * Return all track root paths with true/false indicating whether they are included in the RAG
   * scope.
   */
  public Map<String, Boolean> getRagScope() {
    try {
      List<String> allPaths =
          trackRootRepository.findAll().stream().map(TrackRoot::getPath).toList();
      if (scopedTrackRootPaths == null) {
        return allPaths.stream().collect(Collectors.toMap(p -> p, p -> true));
      }
      return allPaths.stream()
          .collect(Collectors.toMap(p -> p, scopedTrackRootPaths::contains));
    } catch (Exception ex) {
      throw new APIServiceException(ErrorCode.RAG_SCOPE_GET_FAILED, "Failed to get RAG scope", ex);
    }
  }

  public void setRagScope(Set<String> trackRootPaths) {
    try {
      this.scopedTrackRootPaths = new HashSet<>(trackRootPaths);
    } catch (Exception ex) {
      throw new APIServiceException(ErrorCode.RAG_SCOPE_SET_FAILED, "Failed to set RAG scope", ex);
    }
  }

  private Set<Long> resolveContextIds() {
    if (scopedTrackRootPaths == null) {
      return null; // search all
    }
    List<TrackRoot> trackRoots = trackRootRepository.findByPaths(scopedTrackRootPaths);
    if (trackRoots.isEmpty()) {
      return Set.of();
    }
    List<Long> trackRootIds = trackRoots.stream().map(TrackRoot::getId).toList();
    return new HashSet<>(contextRepository.findAllIdsByTrackRootIds(trackRootIds));
  }
}
