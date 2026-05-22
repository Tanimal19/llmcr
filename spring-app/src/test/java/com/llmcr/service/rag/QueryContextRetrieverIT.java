package com.llmcr.service.rag;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;


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
import com.llmcr.entity.Context.ContextType;
import com.llmcr.entity.Source.SourceType;

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

    private final TopKSelectionStrategy mockTopStrategy = (contexts, k) -> contexts.stream().limit(k).toList();

    private static final Logger logger = LoggerFactory.getLogger(ContextRepositoryIT.class);
    
    @BeforeEach
    void setUp(TestInfo testInfo)
    {
        logger.info("Ready to test: {}", testInfo.getDisplayName());
        ReflectionTestUtils.setField(queryContextRetriever, "rerankingModel", rerankingModel);

        embeddingModel = mock(EmbeddingModel.class);
        ReflectionTestUtils.setField(vectorStore, "embeddingModel", embeddingModel);
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

    @Test
    @DisplayName("S3-1-1: Successful RAG")
    void testS3_1_1_SuccessfulRAG()
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
}
