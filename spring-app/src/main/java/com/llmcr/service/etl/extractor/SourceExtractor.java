package com.llmcr.service.etl.extractor;

import com.llmcr.entity.Context;
import com.llmcr.entity.Source;
import java.util.List;
import java.util.function.Function;

/**
 * ContextExtractor is responsible for extracting context information from a
 * given source.
 */
public interface SourceExtractor extends Function<Source, List<Context>> {
    boolean supports(Source source);
}
