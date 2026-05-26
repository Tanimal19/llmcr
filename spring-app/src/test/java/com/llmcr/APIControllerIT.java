package com.llmcr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.llmcr.feature.sync.ConfigSyncService;
import com.llmcr.feature.chat.ChatService;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Transactional
public class APIControllerIT extends BaseIntegrationTest {

    @Autowired
    APIController apiController;

    @MockitoBean
    ChatService chatService;

    @MockitoBean
    ConfigSyncService configSyncService;

    @MockitoBean
    com.llmcr.feature.sync.etl.LoadService loadService;

    private static final Logger logger = LoggerFactory.getLogger(APIControllerIT.class);

    @BeforeEach
    void setup(TestInfo testInfo) {
        logger.info("Ready to test: {}", testInfo.getDisplayName());
    }

    @Test
    @DisplayName("S1-3-1: Blank chat request is rejected")
    void testS1_3_1() {
        assertThatThrownBy(() -> apiController.chat(new APIController.ChatRequest("")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(responseStatusException.getReason()).isEqualTo("query must not be blank");
                });

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("S1-5-1: Chat request is forwarded to ChatService")
    void testS1_5_1() {
        String query = "test query";

        when(chatService.chat(query)).thenReturn(new ChatService.ChatResponse(
                "answer",
                Map.of("context-1", 0.91f)));

        ChatService.ChatResponse response = apiController.chat(new APIController.ChatRequest(query));

        assertThat(response.answer()).isEqualTo("answer");
        assertThat(response.retrievedContexts()).containsEntry("context-1", 0.91f);
        verify(chatService).chat(query);
    }

    @Test
    @DisplayName("S1-6-1: ChatService failure is propagated by controller")
    void testS1_6_1() {
        String query = "test query";
        RuntimeException failure = new RuntimeException("LLM error");

        when(chatService.chat(query)).thenThrow(failure);

        assertThatThrownBy(() -> apiController.chat(new APIController.ChatRequest(query)))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("S5-1-1: setRagScope forwards paths to ChatService")
    void testS5_1_1() {
        String path = "/some/path";
        Set<String> paths = Set.of(path);

        assertThatCode(() -> apiController.setRagScope(new APIController.SetRagRequest(paths)))
                .doesNotThrowAnyException();

        verify(chatService).setRagScope(paths);
    }

    @Test
    @DisplayName("S5-3-1: getRagScope delegates to ChatService")
    void testS5_3_1() {
        Map<String, Boolean> expected = Map.of("/some/path", true);
        when(chatService.getRagScope()).thenReturn(expected);

        assertThat(apiController.getRagScope()).isEqualTo(expected);

        verify(chatService).getRagScope();
    }

    @Test
    @DisplayName("S5-4-1: Blank RAG request is rejected")
    void testS5_4_1() {
        assertThatThrownBy(() -> apiController.setRagScope(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(responseStatusException.getReason()).isEqualTo("trackRootPaths must not be null");
                });

        assertThatThrownBy(() -> apiController.setRagScope(new APIController.SetRagRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(responseStatusException.getReason()).isEqualTo("trackRootPaths must not be null");
                });

        verifyNoInteractions(chatService);
    }
}
