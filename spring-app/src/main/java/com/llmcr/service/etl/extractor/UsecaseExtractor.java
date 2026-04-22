package com.llmcr.service.etl.extractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.JsonMetadataGenerator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.entity.Source;

@Component
public class UsecaseExtractor implements SourceExtractor {

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.JSON;
    }

    @Override
    public List<Context> apply(Source source) {
        JsonMetadataGenerator metadataGenerator = jsonMap -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("description", jsonMap.get("description"));
            return metadata;
        };

        JsonReader reader = new JsonReader(
                new FileSystemResource(source.getPath()),
                metadataGenerator,
                "content");

        List<Document> docs = reader.read();
        AtomicInteger contextIndex = new AtomicInteger(0);
        return docs.stream()
                .map(doc -> {
                    Context context = new Context(
                            source,
                            contextIndex.getAndIncrement(),
                            "Usecase::" + source.getSourceName() + "::" + contextIndex.get(),
                            doc.getText(),
                            ContextType.USECASE);
                    context.addChunk(new Chunk(context, 0, doc.getMetadata().get("description").toString()));
                    return context;
                })
                .toList();
    }
}