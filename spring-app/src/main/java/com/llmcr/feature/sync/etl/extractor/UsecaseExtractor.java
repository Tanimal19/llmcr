package com.llmcr.feature.sync.etl.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.entity.Source;
import com.llmcr.domain.entity.Context.ContextType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

@Deprecated
@Component
public class UsecaseExtractor implements SourceExtractor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(Source source) {
        if (source.getType() != Source.SourceType.JSON) {
            return false;
        }
        JsonNode rootNode = readRootNode(source);
        return rootNode != null && isUsecaseArray(rootNode);
    }

    @Override
    public List<Context> apply(Source source) {
        JsonNode rootNode = readRootNode(source);
        if (rootNode == null || !rootNode.isArray()) {
            return List.of();
        }

        List<Context> contexts = new ArrayList<>();
        AtomicInteger contextIndex = new AtomicInteger(0);

        for (JsonNode item : rootNode) {
            String prId = item.path("prId").asText("");
            String checklistItem = item.path("checklistItem").asText("");
            String name = "Usecase::" + prId + "::" + checklistItem;

            String content;
            try {
                String response = objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(item.path("response"));
                content = "Checklist item: " + checklistItem + "\n" + "Response: " + response;
            } catch (Exception e) {
                content = "Checklist item: " + checklistItem + "\n" + "Response: " + item.path("response").toString();
            }

            contexts.add(new Context(source, contextIndex.getAndIncrement(), name, content, ContextType.USECASE));
        }

        return contexts;
    }

    private boolean isUsecaseArray(JsonNode rootNode) {
        if (!rootNode.isArray()) {
            return false;
        }
        for (JsonNode item : rootNode) {
            if (!hasNonNullValue(item, "prId") || !hasNonNullValue(item, "checklistItem")) {
                return false;
            }
        }
        return true;
    }

    private JsonNode readRootNode(Source source) {
        try {
            return objectMapper.readTree(new FileSystemResource(source.getPath()).getInputStream());
        } catch (IOException e) {
            return null;
        }
    }

    private boolean hasNonNullValue(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        return fieldNode != null && !fieldNode.isNull();
    }
}
