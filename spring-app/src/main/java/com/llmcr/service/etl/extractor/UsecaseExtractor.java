package com.llmcr.service.etl.extractor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.llmcr.entity.Context.ContextType;

@Component
public class UsecaseExtractor extends JsonExtractor {

    @Override
    protected ExtractorSchema getSchema() {
        return new ExtractorSchema(
                "usecase",
                List.of("id", "description"),
                List.of("id", "description"),
                "Usecase",
                ContextType.USECASE,
                "description");
    }
}