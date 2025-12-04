package com.example.llmcr.datasource;

import com.example.llmcr.extractor.VoidRawDataExtractor;

/**
 * Source for PDF files.
 */
public class PdfSource implements RawDataSource {
    private final String path;

    public PdfSource(String path) {
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
