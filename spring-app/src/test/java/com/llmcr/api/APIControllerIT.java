package com.llmcr.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.llmcr.APIController;
import com.llmcr.BaseIntegrationTest;
import com.llmcr.domain.entity.Chunk;
import com.llmcr.domain.entity.ChunkCollection;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.entity.Source;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.exception.APIServiceException.ErrorCode;
import com.llmcr.domain.repository.ChunkCollectionRepository;
import com.llmcr.domain.repository.TrackRootRepository;
import com.llmcr.feature.chat.ChatService;
import com.llmcr.infrastructure.agent.logging.AgentLoggerAdvisor;
import com.llmcr.infrastructure.etl.LoadService;
import com.llmcr.infrastructure.rag.QueryContextRetriever;
import com.llmcr.infrastructure.vectorstore.faiss.FaissService;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class APIControllerIT extends BaseIntegrationTest {

    @Autowired
    APIController apiController;

    @Autowired
    ChatService chatService;

    @Autowired
    LoadService loadService;

    @MockitoBean
    TrackRootRepository trackRootRepository;

    @MockitoBean
    ChunkCollectionRepository chunkCollectionRepository;

    @MockitoBean
    FaissService faissService;

    @MockitoBean
    QueryContextRetriever queryContextRetriever;

    // @MockitoBean
    // ModelClientFactory modelClientFactory;

    @MockitoBean
    ChatClient chatClient;

    private static final Logger logger = LoggerFactory.getLogger(APIControllerIT.class);

    @BeforeEach
    void setup(TestInfo testInfo) {
        logger.info("Ready to test: {}", testInfo.getDisplayName());
    }

    @Test
    @DisplayName("S1-3-1: Empty user input")
    void testS1_3_1() {
        ChatService.ChatResponse response = chatService.chat("");
        assertThat(response.answer()).isEqualTo("(no query provided)");
        assertThat(response.retrievedContexts().size()).isEqualTo(0);
    }

    @Test
    @DisplayName("S1-5-1: Fail to get RAG result")
    void testS1_5_1() {
        String query = "test query";
        when(queryContextRetriever.retrieve(any())).thenThrow(new RuntimeException("retrieve failed"));
        assertThatThrownBy(() -> chatService.chat(query))
                .isInstanceOf(APIServiceException.class)
                .satisfies(ex -> assertThat(((APIServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RAG_RETRIEVAL_FAILED));
    }

    @Test
    @DisplayName("S1-6-1: Fail to get LLM response")
    void testS1_6_1() {
        String query = "test query";
        ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(queryContextRetriever.retrieve(any())).thenReturn(List.of());
        when(modelClientFactory.createChatClient(any(), any())).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(AgentLoggerAdvisor.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenThrow(new RuntimeException("LLM error"));

        assertThatThrownBy(() -> chatService.chat(query))
                .isInstanceOf(APIServiceException.class)
                .satisfies(ex -> assertThat(((APIServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.LLM_RESPONSE_FAILED));
    }

    @Test
    @DisplayName("S5-1-1: Modification success")
    void testS5_1_1() {
        String path = "/some/path";
        Set<String> paths = Set.of(path);

        TrackRoot trackRoot = new TrackRoot(path, Set.of(Source.SourceType.PDF));
        Source source = new Source(path + "/file.pdf", "abc123", Source.SourceType.PDF);
        trackRoot.addSource(source);
        Context context = new Context(source, 0, "ctx", "content", Context.ContextType.DOCUMENT);
        Chunk chunk = new Chunk("chunk content");
        chunk.setEmbedding(new float[] { 0.1f, 0.2f, 0.3f });
        context.addChunk(chunk);

        ChunkCollection collection = new ChunkCollection(ChatService.COLLECTION_NAME, new HashSet<>());

        when(trackRootRepository.findByPaths(paths)).thenReturn(List.of(trackRoot));
        when(chunkCollectionRepository.findByName(ChatService.COLLECTION_NAME)).thenReturn(Optional.of(collection));

        assertThatCode(() -> apiController.setRagScope(new APIController.SetRagRequest(paths)))
                .doesNotThrowAnyException();

        verify(chunkCollectionRepository).save(collection);
        assertThat(collection.getTrackRoots()).containsExactly(trackRoot);
        assertThat(collection.getChunks()).containsExactly(chunk);
    }

    @Test
    @DisplayName("S5-3-1: Failed to list database items")
    void testS5_3_1() {
        when(trackRootRepository.findAll()).thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> apiController.getRagScope())
                .isInstanceOf(APIServiceException.class)
                .satisfies(ex -> assertThat(((APIServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RAG_SCOPE_GET_FAILED));
    }

    @Test
    @DisplayName("S5-4-1: Failed to update the configuration")
    void testS5_4_1() {
        when(chunkCollectionRepository.findByName(ChatService.COLLECTION_NAME)).thenThrow(
                new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> apiController.setRagScope(new APIController.SetRagRequest(Set.of("/some/path"))))
                .isInstanceOf(APIServiceException.class)
                .satisfies(ex -> assertThat(((APIServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RAG_SCOPE_SET_FAILED));
    }
}
