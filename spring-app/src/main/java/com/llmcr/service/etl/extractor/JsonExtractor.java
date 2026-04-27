package com.llmcr.service.etl.extractor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonMetadataGenerator;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.FileSystemResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.entity.Source;

public abstract class JsonExtractor implements SourceExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonExtractor.class);

    public record ExtractorSchema(
            String rootField, // 初始檢查：items 是否含此欄位
            List<String> metadataFields, // JsonReader metadata + required 檢查
            String contentField, // JsonReader 的 key + required 檢查
            String namePrefix,
            ContextType contextType) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected abstract ExtractorSchema getSchema();

    @Override
    public boolean supports(Source source) {
        boolean result = source.getType() == Source.SourceType.JSON && matchesSchema(source);
        log.info("supports({}) -> {}", source.getSourceName(), result);
        return result;
    }

    @Override
    public List<Context> apply(Source source) {
        log.info("Extracting contexts from source: {}", source.getSourceName());
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
                schema.contentField());

        List<Document> docs = reader.read();
        log.info("Read {} documents from source: {}", docs.size(), source.getSourceName());
        AtomicInteger contextIndex = new AtomicInteger(0);
        return docs.stream()
                .map(doc -> {
                    Context context = new Context(
                            source,
                            contextIndex.getAndIncrement(),
                            schema.namePrefix() + "::" + source.getSourceName() + "::" + contextIndex.get(),
                            doc.getText(),
                            schema.contextType());
                    return context;
                })
                .toList();
    }

    protected boolean validateJson(Source source, JsonNode rootNode) {
        ExtractorSchema schema = getSchema();
        if (!rootNode.isArray()) {
            log.error("JSON root is not an array in source: {}", source.getSourceName());
            return false;
        }

        for (JsonNode item : rootNode) {
            if (!hasNonNullValue(item, schema.rootField())) {
                log.error("JSON item is missing root field '{}' in source: {}", schema.rootField(),
                        source.getSourceName());
                return false;
            }
            if (!hasNonNullValue(item, schema.contentField())) {
                log.error("JSON item is missing content field '{}' in source: {}", schema.contentField(),
                        source.getSourceName());
                return false;
            }
            for (String field : schema.metadataFields()) {
                if (!hasNonNullValue(item, field)) {
                    log.error("JSON item is missing metadata field '{}' in source: {}", field,
                            source.getSourceName());
                    return false;
                }
            }
        }
        return true;
    }

    protected JsonNode readRootNode(Source source) {
        try {
            return objectMapper.readTree(new FileSystemResource(source.getPath()).getInputStream());
        } catch (IOException e) {
            log.error("Failed to read JSON from source: {}", source.getPath(), e);
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