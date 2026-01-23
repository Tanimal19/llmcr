package com.example.llmcr.extractor;

import com.example.llmcr.datasource.*;
import com.example.llmcr.entity.ClassNode;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts ClassNode objects from various data sources.
 */
public class ClassNodeExtractor implements VoidDataSourceExtractor<List<ClassNode>> {

    public ClassNodeExtractor() {
    }

    @Override
    public List<ClassNode> visit(CompilationUnitSource source) {
        CompilationUnit cu = source.getCu();
        String packageName = cu.getPackageDeclaration().isPresent()
                ? cu.getPackageDeclaration().get().getNameAsString()
                : "";

        List<ClassNode> classNodes = Stream.of(
                cu.findAll(ClassOrInterfaceDeclaration.class),
                cu.findAll(EnumDeclaration.class),
                cu.findAll(RecordDeclaration.class))
                .flatMap(List::stream)
                .map(typeDecl -> {
                    String signature = packageName + "." + typeDecl.getNameAsString();
                    String code = typeDecl.toString();
                    return new ClassNode(source.getSourceName(), code, signature);
                })
                .collect(Collectors.toList());

        return classNodes;
    }

    @Override
    public List<ClassNode> visit(PdfSource source) {
        return new ArrayList<>();
    }

    @Override
    public List<ClassNode> visit(AsciiDocSource source) {
        return new ArrayList<>();
    }

    @Override
    public List<ClassNode> visit(MarkdownSource source) {
        return new ArrayList<>();
    }
}
