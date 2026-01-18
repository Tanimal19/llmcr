package com.example.llmcr.datasource;

import java.nio.file.Path;

import com.example.llmcr.extractor.VoidDataSourceExtractor;

/**
 * Source for PDF files.
 */
public class PdfSource implements DataSource {
    private final Path path;

    public PdfSource(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public String getSourceCtx() {
        return "pdf::" + path.getFileName();
    }

    @Override
    public <T> T accept(VoidDataSourceExtractor<T> extractor) {
        return extractor.visit(this);
    }
}
