package com.example.llmcr.datasource;

import java.nio.file.Path;

import com.example.llmcr.extractor.VoidRawDataExtractor;

/**
 * Source for Markdown files.
 */
public class MarkdownSource implements RawDataSource {
    private final Path path;

    public MarkdownSource(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    @Override
    public <T> T accept(VoidRawDataExtractor<T> extractor) {
        return extractor.visit(this);
    }
}
