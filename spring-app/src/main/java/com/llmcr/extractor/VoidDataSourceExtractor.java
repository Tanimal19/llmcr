package com.llmcr.extractor;

import com.llmcr.datasource.AsciiDocSource;
import com.llmcr.datasource.CompilationUnitSource;
import com.llmcr.datasource.MarkdownSource;
import com.llmcr.datasource.PdfSource;

/**
 * Interface for extracting data from raw sources.
 * Follows the Visitor design pattern.
 */
public interface VoidDataSourceExtractor<T> {
    T visit(CompilationUnitSource source);

    T visit(PdfSource source);

    T visit(AsciiDocSource source);

    T visit(MarkdownSource source);
}
