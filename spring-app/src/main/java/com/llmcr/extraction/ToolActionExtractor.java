package com.llmcr.extraction;

import java.util.List;

import com.llmcr.entity.Source;
import com.llmcr.entity.contextImpl.ToolAction;

public class ToolActionExtractor implements ContextExtractor<ToolAction> {

    private static final actionRegistry = Map.of(
        "get_feedback"
    )

    public ToolActionExtractor() {
    }

    @Override
    public boolean supports(Source source) {
        return false;
    }

    @Override
    public List<ToolAction> extract(Source source) {
        // TODO: Implement the extraction logic to parse the XML content and create
        // ToolFunction instances.
    }

}
