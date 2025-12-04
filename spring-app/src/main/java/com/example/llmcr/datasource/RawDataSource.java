package com.example.llmcr.datasource;

import com.example.llmcr.extractor.VoidRawDataExtractor;

/**
 * Interface for all raw data sources.
 * Follows the Acceptor pattern to enable the Visitor pattern.
 */
public interface RawDataSource {
    <T> T accept(VoidRawDataExtractor<T> extractor);
}
