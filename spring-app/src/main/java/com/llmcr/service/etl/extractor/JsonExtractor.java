package com.llmcr.service.etl.extractor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonMetadataGenerator;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.FileSystemResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.entity.Source;

public abstract class JsonExtractor implements SourceExtractor {

    public record ExtractorSchema(
            String rootField,
            List<String> requiredFields,
            List<String> metadataFields,
            String namePrefix,
            ContextType contextType,
            String chunkField) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected abstract ExtractorSchema getSchema();

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.JSON && matchesSchema(source);
    }

    @Override
    public List<Context> apply(Source source) {
        ExtractorSchema schema = getSchema();
        JsonMetadataGenerator metadataGenerator = jsonMap -> {
            Map<String, Object> metadata = new HashMap<>();
            for (String field : schema.metadataFields()) {
                metadata.put(field, jsonMap.get(field));
            }
            return metadata;
        };

        JsonReader reader = new JsonReader(
                new FileSystemResource(source.getPath()),
                metadataGenerator,
                schema.rootField());

        List<Document> docs = reader.read();
        AtomicInteger contextIndex = new AtomicInteger(0);
        return docs.stream()
                .map(doc -> {
                    Context context = new Context(
                            source,
                            contextIndex.getAndIncrement(),
                            schema.namePrefix() + "::" + source.getSourceName() + "::" + contextIndex.get(),
                            doc.getText(),
                            schema.contextType());
                    if (schema.chunkField() != null) {
                        context.addChunk(new Chunk(doc.getMetadata().get(schema.chunkField()).toString()));
                    }
                    return context;
                })
                .toList();
    }

    protected boolean validateJson(Source source, JsonNode rootNode) {
        ExtractorSchema schema = getSchema();
        JsonNode arrayNode = rootNode.get(schema.rootField());
        if (arrayNode == null || !arrayNode.isArray())
            return false;
        for (JsonNode item : arrayNode) {
            for (String field : schema.requiredFields()) {
                if (!hasNonNullValue(item, field))
                    return false;
            }
        }
        return true;
    }

    protected JsonNode readRootNode(Source source) {
        try {
            return objectMapper.readTree(new FileSystemResource(source.getPath()).getInputStream());
        } catch (IOException e) {
            return null;
        }
    }

    protected boolean hasNonNullValue(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        return fieldNode != null && !fieldNode.isNull();
    }

    private boolean matchesSchema(Source source) {
        JsonNode rootNode = readRootNode(source);
        return rootNode != null && validateJson(source, rootNode);
    }
}