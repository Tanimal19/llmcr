package com.llmcr.extractor;

import java.util.List;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context.ContextDocument;

/**
 * ContextExtractor is responsible for extracting context information from a
 * given source.
 */
public interface ContextExtractor {
    boolean supports(Source source);

    List<ContextDocument> extract(Source source);
}
