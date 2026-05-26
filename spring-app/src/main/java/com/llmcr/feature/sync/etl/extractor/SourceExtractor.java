package com.llmcr.feature.sync.etl.extractor;

import java.util.List;
import java.util.function.Function;

import com.llmcr.domain.entity.Context;
import com.llmcr.domain.entity.Source;

/**
 * ContextExtractor is responsible for extracting context information from a
 * given source.
 */
public interface SourceExtractor extends Function<Source, List<Context>> {
    boolean supports(Source source);
}
