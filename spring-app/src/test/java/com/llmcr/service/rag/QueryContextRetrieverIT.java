package com.llmcr.service.rag;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.BaseIntegrationTest;
import com.llmcr.api.APIServiceException.ErrorCode;
import com.llmcr.repository.ChunkRepository;
import com.llmcr.repository.ContextRepository;
import com.llmcr.repository.ContextRepositoryIT;
import com.llmcr.repository.SourceRepository;
import com.llmcr.reranking.RerankingModel;
import com.llmcr.service.FaissService;
import com.llmcr.service.FaissService.SearchVectorsResponse;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.TopKSelectionStrategy;
import com.llmcr.vectorstore.MyVectorStore;
import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.entity.Source;

@Transactional
public class QueryContextRetrieverIT extends BaseIntegrationTest
{
    @Autowired
    private QueryContextRetriever queryContextRetriever;
    
    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private MyVectorStore vectorStore;

    @MockitoBean
    private FaissService faissService;

    @MockitoBean
    private RerankingModel rerankingModel;

    private EmbeddingModel embeddingModel;

    private final TopKSelectionStrategy mockTopStrategy = spy(new TopKSelectionStrategy() 
    {
        @Override
        public List<ContextScorePair> select(List<ContextScorePair> contexts, int k)
        {
            return contexts.stream().limit(k).toList();
        }
    });

    private static final Logger logger = LoggerFactory.getLogger(ContextRepositoryIT.class);
    
    @BeforeEach
    void setUp(TestInfo testInfo)
    {
        logger.info("Ready to test: {}", testInfo.getDisplayName());
        ReflectionTestUtils.setField(queryContextRetriever, "rerankingModel", rerankingModel);

        embeddingModel = mock(EmbeddingModel.class);
        ReflectionTestUtils.setField(vectorStore, "embeddingModel", embeddingModel);

        // rerankingModel = mock(RerankingModel.class);
        // ReflectionTestUtils.setField(queryContextRetriever, "rerankingModel", rerankingModel);
    }

    private Long setupMockDataAndGetChunkId(String contextName) {
        Source source = new Source("/test_" + contextName, "hash_" + contextName, Source.SourceType.PDF);
        source = sourceRepository.save(source);
        Context ctx = new Context(source, 0, contextName, "content", Context.ContextType.DOCUMENT);
        ctx = contextRepository.save(ctx);
        
        Chunk chunk = new Chunk(ctx, 1, "chunk content");
        chunk = chunkRepository.save(chunk);
        
        return chunk.getId();
    }

    /* Doesn't test the case with reranking option on */
    @Test
    @DisplayName("S3-1-1: Successful RAG")
    void testS3_1_1()
    {
        Long chunkId = setupMockDataAndGetChunkId("contextA");

        float[] fakeQueryVector = {0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed(anyString())).thenReturn(fakeQueryVector);

        SearchVectorsResponse fakeFaissResponse = new SearchVectorsResponse(List.of(chunkId), List.of(0.95f));
        when(faissService.searchVectors(any())).thenReturn(fakeFaissResponse);

        ContextRetrievalConfiguration config = new ContextRetrievalConfiguration(5, mockTopStrategy, "text_collection", false);
        ContextRetrievalRequest request = new ContextRetrievalRequest(List.of("How to test RAG?"), config);
        List<ContextScorePair> results = queryContextRetriever.retrieve(request);

        assertThat(results).isNotEmpty();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(0.95f);
    }

    // @Test
    // @DisplayName("S3-1-2: Successful RAG with verbose")
    // void test_1_2_SuccessfullRAGWithVerbose()
    // {

    // }

    /*Doesn't test with multiple queries */
    @Test
    @DisplayName("S3-2-1:  Number of data chunks is less than K")
    void testS3_2_1()
    {
        Long chunkId1 = setupMockDataAndGetChunkId("contextA");
        Long chunkId2 = setupMockDataAndGetChunkId("contextB");

        int expectedk = 10;
        ContextRetrievalConfiguration config = new ContextRetrievalConfiguration(expectedk, mockTopStrategy, "text_collection", false);
        ContextRetrievalRequest request = new ContextRetrievalRequest(List.of("test"), config);

        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(faissService.searchVectors(any())).thenReturn(new SearchVectorsResponse(List.of(chunkId1, chunkId2), List.of(0.9f, 0.8f)));

        List<ContextScorePair> results = queryContextRetriever.retrieve(request);
        
        assertThat(results).hasSize(2);
        verify(mockTopStrategy, never()).select(anyList(), anyInt());
    }

    // @Test
    // @DisplayName("S3-2-2: Number of data chunks is less than K with verbose")
    // void test_1_2_SuccessfullRAGWithVerbose()
    // {

    // }

    @Test
    @DisplayName("S3-3-1: Query Embedding fails")
    void testS3_3_1()
    {
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("Api Error during query embedding"));

        ContextRetrievalConfiguration config = new ContextRetrievalConfiguration(5, mockTopStrategy, "text_collection", false);
        ContextRetrievalRequest request = new ContextRetrievalRequest(List.of("test"), config);
        assertThatThrownBy(() -> queryContextRetriever.retrieve(request)).isInstanceOf(RuntimeException.class).hasMessage(ErrorCode.RAG_VECTOR_SEARCH_FAILED.defaultMessage());
    }

    @Test
    @DisplayName("S3-4-1: Retrieval fails")
    void testS3_4_1()
    {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(faissService.searchVectors(any())).thenThrow(new RuntimeException("Database connection timeout during data retrieval"));

        ContextRetrievalConfiguration config = new ContextRetrievalConfiguration(5, mockTopStrategy, "text_collection", false);
        ContextRetrievalRequest request = new ContextRetrievalRequest(List.of("test"), config);

        assertThatThrownBy(() -> queryContextRetriever.retrieve(request)).isInstanceOf(RuntimeException.class).hasMessage(ErrorCode.RAG_VECTOR_SEARCH_FAILED.defaultMessage());
    }
}
