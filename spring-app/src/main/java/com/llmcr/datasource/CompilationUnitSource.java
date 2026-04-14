package com.llmcr.datasource;

import com.github.javaparser.ast.CompilationUnit;
import com.llmcr.extractor.VoidDataSourceExtractor;

/**
 * Source for compilation units (e.g., code repository).
 */
public class CompilationUnitSource implements DataSource {
    private final CompilationUnit cu;

    public CompilationUnitSource(CompilationUnit cu) {
        this.cu = cu;
    }

    public CompilationUnit getCu() {
        return cu;
    }

    public String getSourceName() {
        return "cu::" + cu.getStorage().map(s -> s.getFileName()).orElse("unknown");
    }

    @Override
    public <T> T accept(VoidDataSourceExtractor<T> extractor) {
        return extractor.visit(this);
    }
}
