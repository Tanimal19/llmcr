package com.llmcr.extractor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.FileSystemResource;

import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextDocument;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.entity.Source;

public class UsecaseExtractor implements ContextExtractor {

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.JSON;
    }

    @Override
    public List<ContextDocument> extract(Source source) {
        JsonReader reader = new JsonReader(
                new FileSystemResource(source.getPath()),
                "usecase");

        List<Document> docs = reader.read();
        AtomicInteger contextIndex = new AtomicInteger(0);
        return docs.stream()
                .map(doc -> new ContextDocument(
                        doc,
                        new Context(
                                source,
                                contextIndex.getAndIncrement(),
                                "Usecase::" + source.getSourceName() + "::" + contextIndex.get(),
                                doc.getText(),
                                ContextType.USECASE)))
                .toList();
    }
}