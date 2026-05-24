package com.llmcr.api;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.BaseIntegrationTest;
import com.llmcr.api.APIServiceException.ErrorCode;
import com.llmcr.entity.Chunk;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.Context;
import com.llmcr.entity.Source;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.service.ChatService;
import com.llmcr.service.FaissService;
import com.llmcr.service.etl.LoadService;

@Transactional
public class APIControllerIT extends BaseIntegrationTest
{
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

    private static final Logger logger = LoggerFactory.getLogger(APIControllerIT.class);

    @BeforeEach()
    void setup(TestInfo testInfo)
    {
        logger.info("Ready to test: {}", testInfo.getDisplayName());
    }

    @Test
    @DisplayName("S5-1-1: Modification success")
    void testS5_1_1()
    {
        String path = "/some/path";
        Set<String> paths = Set.of(path);

        TrackRoot trackRoot = new TrackRoot(path, Set.of(Source.SourceType.PDF));
        Source source = new Source(path + "/file.pdf", "abc123", Source.SourceType.PDF);
        trackRoot.addSource(source);
        Context context = new Context(source, 0, "ctx", "content", Context.ContextType.DOCUMENT);
        Chunk chunk = new Chunk("chunk content");
        chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
        context.addChunk(chunk);

        ChunkCollection collection = new ChunkCollection(ChatService.COLLECTION_NAME, new HashSet<>());

        when(trackRootRepository.findByPaths(paths)).thenReturn(List.of(trackRoot));
        when(chunkCollectionRepository.findByName(ChatService.COLLECTION_NAME)).thenReturn(Optional.of(collection));

        assertThatCode(() -> apiController.setRagScope(new APIController.RagScopeRequest(paths)))
                .doesNotThrowAnyException();

        verify(chunkCollectionRepository).save(collection);
        assertThat(collection.getTrackRoots()).containsExactly(trackRoot);
        assertThat(collection.getChunks()).containsExactly(chunk);
    }

    @Test
    @DisplayName("S5-3-1: Failed to list database items")
    void testS5_3_1()
    {
        when(trackRootRepository.findAll()).thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> apiController.getRagScope())
                .isInstanceOf(APIServiceException.class)
                .satisfies(ex -> assertThat(((APIServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RAG_SCOPE_GET_FAILED));
    }

    @Test
    @DisplayName("S5-4-1: Failed to update the configuration")
    void testS5_4_1()
    {
        when(chunkCollectionRepository.findByName(ChatService.COLLECTION_NAME))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> apiController.setRagScope(new APIController.RagScopeRequest(Set.of("/some/path"))))
                .isInstanceOf(APIServiceException.class)
                .satisfies(ex -> assertThat(((APIServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RAG_SCOPE_SET_FAILED));
    }
}
