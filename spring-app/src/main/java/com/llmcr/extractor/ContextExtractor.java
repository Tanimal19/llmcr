package com.llmcr.extractor;

import java.util.List;
import java.util.function.Function;

import com.llmcr.entity.Context;
import com.llmcr.entity.Source;

/**
 * Interface for extracting Content from Sources.
 */
public interface ContextExtractor<S extends Context> extends Function<Source, List<S>> {
    boolean supports(Source source);

    List<S> apply(Source source);
}
