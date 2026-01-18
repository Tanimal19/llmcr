package com.example.llmcr.datasource;

import java.nio.file.Path;

import com.example.llmcr.extractor.VoidDataSourceExtractor;

/**
 * Source for Markdown files.
 */
public class MarkdownSource implements DataSource {
    private final Path path;

    public MarkdownSource(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public String getSourceCtx() {
        return "markdown::" + path.getFileName();
    }

    @Override
    public <T> T accept(VoidDataSourceExtractor<T> extractor) {
        return extractor.visit(this);
    }
}
