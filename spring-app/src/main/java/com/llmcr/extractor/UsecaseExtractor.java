package com.llmcr.extractor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.FileSystemResource;

import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.entity.Source;

public class UsecaseExtractor implements ContextExtractor {

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.JSON;
    }

    @Override
    public List<Document> extract(Source source) {
        JsonReader reader = new JsonReader(
                new FileSystemResource(source.getPath()),
                "usecase");

        List<Document> docs = reader.read();
        AtomicInteger contextIndex = new AtomicInteger(0);
        docs.forEach(doc -> {
            doc.getMetadata().put("source", source);
            doc.getMetadata().put("contextIndex", contextIndex.getAndIncrement());
        });
        return docs;
    }

    @Override
    public Context toContext(Document doc) {
        assert doc.getMetadata().containsKey("source") : "Usecase metadata must contain 'source'";
        assert doc.getMetadata().containsKey("contextIndex") : "Usecase metadata must contain 'contextIndex'";

        Source source = (Source) doc.getMetadata().get("source");
        Integer contextIndex = (Integer) doc.getMetadata().get("contextIndex");

        return new Context(
                source,
                contextIndex,
                "Usecase::" + source.getSourceName() + "::" + contextIndex,
                doc.getText(),
                ContextType.USECASE);
    }
}