package com.llmcr.reader;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import com.llmcr.entity.Source;
import com.llmcr.extractor.ContextExtractor;
import com.llmcr.repository.ContextRepository;

public class SourceDocumentReader implements DocumentReader {
    private Source source;
    private ContextRepository contextRepository;
    private List<ContextExtractor> extractors;

    public SourceDocumentReader(Source source, ContextRepository contextRepository, List<ContextExtractor> extractors) {
        this.source = source;
        this.contextRepository = contextRepository;
        this.extractors = extractors;
    }

    @Override
    public List<Document> get() {
        List<Document> documents = new ArrayList<>();
        for (ContextExtractor extractor : extractors) {
            if (extractor.supports(source)) {
                try {
                    List<Document> docs = extractor.extract(source);
                    documents.addAll(docs);

                    // save to database as context
                    docs.forEach(doc -> {
                        contextRepository.save(extractor.toContext(doc));
                    });

                } catch (Exception e) {
                    throw new RuntimeException("Error extracting context from source " + source.getSourceName(), e);
                }
            }
        }
        return documents;
    }
}
