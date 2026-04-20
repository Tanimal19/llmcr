package com.llmcr.extraction;

import java.util.List;

import com.llmcr.entity.Source;
import com.llmcr.entity.contextImpl.ToolFunction;

public class ToolFunctionExtractor implements ContextExtractor<ToolFunction> {

    public ToolFunctionExtractor() {
    }

    @Override
    public boolean supports(Source source) {
        return source.getSourceType() == Source.SourceType.XML;
    }

    @Override
    public List<ToolFunction> extract(Source source) {
        // TODO: Implement the extraction logic to parse the XML content and create
        // ToolFunction instances.
    }

}
