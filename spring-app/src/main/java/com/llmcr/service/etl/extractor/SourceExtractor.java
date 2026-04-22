package com.llmcr.service.etl.extractor;

import java.util.List;
import java.util.function.Function;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;

/**
 * ContextExtractor is responsible for extracting context information from a
 * given source.
 */
public interface SourceExtractor extends Function<Source, List<Context>> {
    boolean supports(Source source);
}
