package com.llmcr.extraction;

import java.util.List;

import com.llmcr.entity.Context;
import com.llmcr.entity.Source;

/**
 * Interface for extracting Content from Sources.
 */
public interface ContextExtractor<T extends Context> {
    boolean supports(Source source);

    List<T> extract(Source source);
}
