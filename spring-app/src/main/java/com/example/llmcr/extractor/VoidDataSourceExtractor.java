package com.example.llmcr.extractor;

import com.example.llmcr.datasource.CompilationUnitSource;
import com.example.llmcr.datasource.PdfSource;
import com.example.llmcr.datasource.AsciiDocSource;
import com.example.llmcr.datasource.MarkdownSource;

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
