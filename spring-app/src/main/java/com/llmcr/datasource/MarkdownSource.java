package com.llmcr.datasource;

import java.nio.file.Path;

import com.llmcr.extractor.VoidDataSourceExtractor;

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

    public String getSourceName() {
        return "markdown::" + path.getFileName();
    }

    @Override
    public <T> T accept(VoidDataSourceExtractor<T> extractor) {
        return extractor.visit(this);
    }
}
