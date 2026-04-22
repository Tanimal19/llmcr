package com.llmcr.service.etl.operation;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

import com.llmcr.entity.Source;
import com.llmcr.extractor.*;
import com.llmcr.repository.ContextRepository;
import com.llmcr.service.etl.ETLPipeline;

/**
 * ExtractOperation is responsible for extracting context from a given Source
 * using a set of ContextExtractors. It returns a list of Documents extracted
 * from the Source and saves the corresponding Contexts to the database.
 */
public class ExtractOperation implements ETLOperation<Source, List<Document>> {

    private static final Logger logger = LoggerFactory.getLogger(ETLPipeline.class);
    private ContextRepository contextRepository;
    private List<ContextExtractor> extractors;

    public ExtractOperation(ContextRepository contextRepository) {
        this.contextRepository = contextRepository;
        this.extractors = List.of(
                new ClassNodeExtractor(),
                new DocumentParagraphExtractor(),
                new UsecaseExtractor(),
                new GuidelineExtractor());

        logger.info("Initialized ExtractOperation with {} extractors", extractors.size());

    }

    @Override
    public List<Document> execute(Source source) {
        List<Document> documents = new ArrayList<>();
        for (ContextExtractor extractor : extractors) {
            if (extractor.supports(source)) {
                try {
                    List<Document> docs = extractor.extract(source);
                    logger.info("Extractor {} produced {} documents for source {}",
                            extractor.getClass().getSimpleName(), docs.size(), source.getSourceName());

                    docs.forEach(doc -> {
                        contextRepository.save(extractor.toContext(doc));
                    });
                    documents.addAll(docs);

                } catch (Exception e) {
                    logger.error("Error extracting context from source {}: {}",
                            source.getSourceName(), e.getMessage());
                }
            }
        }
        logger.info("Extracted {} documents from source {}", documents.size(), source.getSourceName());
        return documents;
    }
}
