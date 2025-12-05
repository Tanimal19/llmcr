package com.example.llmcr.datasource;

import java.nio.file.Path;

import com.example.llmcr.extractor.VoidRawDataExtractor;

/**
 * Source for PDF files.
 */
public class PdfSource implements RawDataSource {
    private final Path path;

    public PdfSource(Path path) {
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
