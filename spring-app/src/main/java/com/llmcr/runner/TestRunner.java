package com.llmcr.runner;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.entity.Context;
import com.llmcr.entity.Source;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.repository.ContextRepository;

/**
 * Reload all chunks from the database into the vector store. This is used to
 * align vector store with the database after a restart, and also to verify that
 * all chunks with embeddings
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "test")
public class TestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    private final ContextRepository contextRepository;

    public TestRunner(ContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Regenerating context names based on source information");

        List<Context> allContexts = contextRepository.findAll();
        int updatedCount = 0;

        for (Context context : allContexts) {
            Source source = context.getSource();
            if (source == null) {
                log.warn("Context {} has no associated source, skipping", context.getId());
                continue;
            }
            if (context.getType() == ContextType.CLASSNODE) {
                // For CLASSNODE, we want to keep the original name which is the fully qualified
                // class name
                continue;
            }

            // Generate context name following the pattern from DocumentParagraphExtractor
            String newName = context.getType() + "::" + source.getPath() + "::" + context.getContextIndex();
            context.setName(newName);
            updatedCount++;
        }

        log.info("Updated {} context names", updatedCount);
    }
}
