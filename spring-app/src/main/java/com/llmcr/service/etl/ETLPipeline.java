package com.llmcr.service.etl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.entity.Source;
import com.llmcr.repository.*;
import com.llmcr.service.etl.extractor.SourceExtractor;
import com.llmcr.service.etl.transformer.ContextTransformer;

@Service
public class ETLPipeline {

    private final SourceRepository sourceRepository;
    private final ContextRepository contextRepository;
    private final ChunkRepository chunkRepository;

    private final List<SourceExtractor> extractors;
    private final List<ContextTransformer> transformers;

    @Autowired
    public ETLPipeline(
            SourceRepository sourceRepository, ContextRepository contextRepository, ChunkRepository chunkRepository,
            List<SourceExtractor> extractors, List<ContextTransformer> transformers) {
        this.sourceRepository = sourceRepository;
        this.contextRepository = contextRepository;
        this.chunkRepository = chunkRepository;
        this.extractors = extractors;
        this.transformers = transformers;
    }

    public void run() {

    }

    private List<Context> extract(Source source) {
        List<Context> contexts = new ArrayList<>();

        for (SourceExtractor extractor : extractors) {
            if (extractor.supports(source)) {
                try {
                    contexts.addAll(extractor.apply(source));
                } catch (Exception e) {
                    throw new RuntimeException("Error extracting context from source " + source.getSourceName(), e);
                }
            }
        }
        return contexts;
    }
}
