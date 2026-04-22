package com.llmcr.service.etl.extractor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.entity.Source;

@Component
public class GuidelineExtractor implements SourceExtractor {

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.JSON;
    }

    @Override
    public List<Context> apply(Source source) {
        JsonReader reader = new JsonReader(
                new FileSystemResource(source.getPath()),
                "guideline");

        List<Document> docs = reader.read();
        AtomicInteger contextIndex = new AtomicInteger(0);
        return docs.stream()
                .map(doc -> new Context(
                        source,
                        contextIndex.getAndIncrement(),
                        "Guideline::" + source.getSourceName() + "::" + contextIndex.get(),
                        doc.getText(),
                        ContextType.GUIDELINE))
                .toList();
    }
}