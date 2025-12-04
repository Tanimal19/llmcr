package com.example.llmcr.datasource;

import com.example.llmcr.extractor.VoidRawDataExtractor;

/**
 * Source for AsciiDoc files.
 */
public class AsciiDocSource implements RawDataSource {
    private final String path;

    public AsciiDocSource(String path) {
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
