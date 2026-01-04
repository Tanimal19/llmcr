package com.example.llmcr.datasource;

import com.example.llmcr.extractor.VoidDataSourceExtractor;
import com.github.javaparser.ast.CompilationUnit;

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

    @Override
    public <T> T accept(VoidDataSourceExtractor<T> extractor) {
        return extractor.visit(this);
    }
}
