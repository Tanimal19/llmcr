package com.llmcr.service.etl.extractor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.llmcr.entity.Context.ContextType;

@Component
public class GuidelineExtractor extends JsonExtractor {

    @Override
    protected ExtractorSchema getSchema() {
        return new ExtractorSchema(
                "guideline",
                List.of("id"),
                "guideline",
                "Guideline",
                ContextType.GUIDELINE);
    }
}