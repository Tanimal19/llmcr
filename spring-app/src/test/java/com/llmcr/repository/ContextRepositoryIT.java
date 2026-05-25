package com.llmcr.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.llmcr.BaseIntegrationTest;
import com.llmcr.entity.*;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.entity.Source.SourceType;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class ContextRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(ContextRepositoryIT.class);

    private Source testSource;
    private Chunk chunkA;
    private Chunk chunkB;
    private Chunk chunkC;
    private Context contextA;
    private Context contextB;

    @BeforeEach
    private void setup(TestInfo testInfo) {
        logger.info("Ready to test: {}", testInfo.getDisplayName());
        testSource = new Source("./test/path/dummy.pdf", "0", SourceType.PDF);
        testSource.setExtracted(false);
        entityManager.persist(testSource);

        chunkA = new Chunk("ChunkA");
        chunkB = new Chunk("ChunkB");
        chunkC = new Chunk("ChunkC");

        contextA = new Context(
            testSource,
            0,
            "contextA",
            chunkA.getContent() + chunkB.getContent(),
            ContextType.USECASE
        );
        contextA.setChunkLoaded(true);
        contextA.setSplitted(false);
        contextA.setEnriched(false);
        contextA.addChunk(chunkA);
        contextA.addChunk(chunkB);
        entityManager.persist(contextA);

        contextB = new Context(testSource, 1, "contextB", chunkC.getContent(), ContextType.DOCUMENT);
        contextB.setChunkLoaded(false);
        contextB.setSplitted(true);
        contextB.setEnriched(true);
        contextB.addChunk(chunkC);
        entityManager.persist(contextB);

        entityManager.flush();
    }

    @Test
    @DisplayName("Test findAllIds: Should successfully retrieve the ID list of all Contexts")
    void testFindAllIds() {
        List<Long> ids = contextRepository.findAllIds();
        assertThat(ids).hasSize(2);
        assertThat(ids).contains(contextA.getId(), contextB.getId());
    }

    @Test
    @DisplayName("Test findAllIdsBySourceId: Should be able to retrieve the ID list of all Contexts under a Source ID")
    void testFindAllIdsBySource() {
        List<Long> ids = contextRepository.findAllIdsBySourceId(testSource.getId());
        assertThat(ids).hasSize(2);
        assertThat(ids).contains(contextA.getId(), contextB.getId());
    }

    @Test
    @DisplayName(
        "Test status filter query: Should correctly filter out unloaded, unsplitted, and unenriched Context IDs"
    )
    void testStatusFilters() {
        List<Long> unloadedIds = contextRepository.findAllUnloadedIds();
        assertThat(unloadedIds).containsOnly(contextB.getId());

        List<Long> unsplittedIds = contextRepository.findAllUnsplittedIds();
        assertThat(unsplittedIds).containsOnly(contextA.getId());

        List<Long> unenrichedIds = contextRepository.findAllUnenrichedIds();
        assertThat(unenrichedIds).containsOnly(contextA.getId());
    }

    @Test
    @DisplayName("Test findByChunkId: Should be able to reverse lookup the corresponding Context via a single Chunk ID")
    void testFindByChunkId() {
        Context foundContext = contextRepository.findByChunkId(chunkA.getId());
        assertThat(foundContext).isNotNull();
        assertThat(foundContext.getId()).isEqualTo(contextA.getId());
        assertThat(foundContext.getName()).isEqualTo("contextA");
    }

    @Test
    @DisplayName(
        "Test findAllByChunkIds: When passing multiple Chunk IDs, it should return a deduplicated Context list"
    )
    void testFindAllByChunkIds() {
        List<Long> chunkIds = Arrays.asList(chunkA.getId(), chunkB.getId(), chunkC.getId());
        List<Context> contexts = contextRepository.findAllByChunkIds(chunkIds);
        assertThat(contexts).hasSize(2);
        assertThat(contexts).extracting(Context::getId).containsExactlyInAnyOrder(contextA.getId(), contextB.getId());
    }

    @Test
    @DisplayName("Test findByFilter: Mixed condition dynamic query and pagination")
    void testFindByFilter() {
        Pageable pageable = PageRequest.of(0, 10);

        // All Empty
        List<Context> allResult = contextRepository.findByFilter(null, null, null, pageable);
        assertThat(allResult).hasSize(2);

        // Filter by type (DOCUMENT)
        List<Context> typeResult = contextRepository.findByFilter(ContextType.DOCUMENT, null, null, pageable);
        assertThat(typeResult).hasSize(1);
        assertThat(typeResult.get(0).getId()).isEqualTo(contextB.getId());

        // Filter by name fuzzy keyword ("Python")
        List<Context> nameResult = contextRepository.findByFilter(null, "B", null, pageable);
        assertThat(nameResult).hasSize(1);
        assertThat(nameResult.get(0).getId()).isEqualTo(contextB.getId());

        // Filter by content fuzzy keyword ("open-source")
        List<Context> contentResult = contextRepository.findByFilter(null, null, "ChunkA", pageable);
        assertThat(contentResult).hasSize(1);
        assertThat(contentResult.get(0).getId()).isEqualTo(contextA.getId());

        // No matching conditions
        List<Context> emptyResult = contextRepository.findByFilter(ContextType.CLASSNODE, "Spring", null, pageable);
        assertThat(emptyResult).isEmpty();
    }
}
