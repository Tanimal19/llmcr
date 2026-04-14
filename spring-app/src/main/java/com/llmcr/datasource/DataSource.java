package com.llmcr.datasource;

import com.llmcr.extractor.VoidDataSourceExtractor;

/**
 * Interface for all raw data sources.
 * Follows the Visitor design pattern.
 */
public interface DataSource {
    <T> T accept(VoidDataSourceExtractor<T> extractor);

    public String getSourceName();
}
