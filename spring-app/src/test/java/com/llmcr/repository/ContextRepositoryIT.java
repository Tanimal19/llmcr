package com.llmcr.repository;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.llmcr.BaseIntegrationTest;
import com.llmcr.entity.Context;
import com.llmcr.entity.Source;

import jakarta.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class ContextRepositoryIT extends BaseIntegrationTest
{
    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Test
    void testFindAllUnloadedIds()
    {
        Source mockSource = new Source("test", Source.SourceType.MARKDOWN);
        mockSource = sourceRepository.save(mockSource);

        Context loadedContext = new Context(mockSource, 0, "loaded", "content", Context.ContextType.DOCUMENT);
        loadedContext.setChunkLoaded(true);

        Context unloadedContext = new Context(mockSource, 1, "unloaded", "content", Context.ContextType.DOCUMENT);
        contextRepository.save(unloadedContext);

        List<Long> unloadedIds = contextRepository.findAllUnloadedIds();

        assertThat(unloadedIds).hasSize(1);
        assertThat(unloadedIds).contains(unloadedContext.getId());
        assertThat(unloadedIds).doesNotContain(loadedContext.getId());
    }
}
