package com.llmcr.etl.extractor;

import java.util.List;
import java.util.function.Function;

import com.llmcr.database.entity.Context;
import com.llmcr.database.entity.Source;

/**
 * ContextExtractor is responsible for extracting context information from a
 * given source.
 */
public interface SourceExtractor extends Function<Source, List<Context>> {
    boolean supports(Source source);
}
