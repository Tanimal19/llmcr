package com.example.llmcr.datasource;

import com.example.llmcr.extractor.VoidRawDataExtractor;

/**
 * Source for Markdown files.
 */
public class MarkdownSource implements RawDataSource {
    private final String path;

    public MarkdownSource(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public <T> T accept(VoidRawDataExtractor<T> extractor) {
        return extractor.visit(this);
    }
}
