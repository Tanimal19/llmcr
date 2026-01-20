package com.example.llmcr.datasource;

import java.nio.file.Path;

import com.example.llmcr.extractor.VoidDataSourceExtractor;

/**
 * Source for AsciiDoc files.
 */
public class AsciiDocSource implements DataSource {
    private final Path path;

    public AsciiDocSource(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public String getSourceName() {
        return "asciidoc::" + path.getFileName();
    }

    @Override
    public <T> T accept(VoidDataSourceExtractor<T> extractor) {
        return extractor.visit(this);
    }
}
