package com.llmcr.service.etl.transformer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.agent.ClassNodeEnrichAgent;
import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;

/**
 * Enrich ClassNode context by generating a summary using LLM.
 */
@Component
public class ClassNodeEnricher implements ContextEnricher {

    private static final Logger logger = LoggerFactory.getLogger(ClassNodeEnricher.class);

    /**
     * Content length below this threshold is unlikely to benefit from enrichment,
     * so we skip to save cost
     */
    private static final int MIN_CONTENT_LENGTH = 1000;

    /**
     * Exclude class names that are likely to be test or configuration classes,
     * which often have less clear functional description and more noisy
     * relationships and usage. This is a heuristic to further reduce unnecessary
     * enrichment cost.
     */
    private static final List<String> CLASSNAME_EXCLUDE = List.of("Test", "Configuration", "Properties");

    private final ClassNodeEnrichAgent classNodeEnrichAgent;

    public ClassNodeEnricher(ClassNodeEnrichAgent classNodeEnrichAgent) {
        this.classNodeEnrichAgent = classNodeEnrichAgent;
    }

    @Override
    public boolean supports(Context context) {
        return context.getType() == Context.ContextType.CLASSNODE;
    }

    @Override
    public Context apply(Context classNode) {
        if (classNode.getContent().length() < MIN_CONTENT_LENGTH) {
            logger.info("Class node content is short ({}), skip enrichment", classNode.getContent().length());
            return classNode;
        }
        for (String exclude : CLASSNAME_EXCLUDE) {
            if (classNode.getName().contains(exclude)) {
                logger.info("Class node name contains '{}', skip enrichment", exclude);
                return classNode;
            }
        }

        ClassNodeEnrichAgent.ClassNodeEnrichOutput enrichment = classNodeEnrichAgent
                .execute(new ClassNodeEnrichAgent.ClassNodeEnrichInput(classNode.getContent()));

        // update class node
        classNode.addChunk(new Chunk(enrichment.functional()));
        classNode.addChunk(new Chunk(enrichment.relationship()));
        classNode.addChunk(new Chunk(enrichment.usage()));

        return classNode;
    }
}