package com.llmcr.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.llmcr.entity.Source;
import com.llmcr.entity.Source.SourceType;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextDocument;
import com.llmcr.entity.Context.ContextType;

import org.springframework.ai.document.Document;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts class nodes from given Java file
 */
public class ClassNodeExtractor implements ContextExtractor {

    private final JavaParser parser;

    public ClassNodeExtractor() {
        this.parser = new JavaParser();
    }

    @Override
    public boolean supports(Source source) {
        return source.getType() == SourceType.JAVACODE;
    }

    @Override
    public List<ContextDocument> extract(Source source) {

        if (source.getPath().equals("package-info.java")) {
            return List.of();
        }

        Path javaPath = Paths.get(source.getPath());
        if (!Files.exists(javaPath) || !Files.isRegularFile(javaPath)) {
            return List.of();
        }

        List<ContextDocument> classNodes = new ArrayList<>();
        try {
            ParseResult<CompilationUnit> result = parser.parse(javaPath);
            result.getResult().ifPresent(cu -> {
                String packageName = cu.getPackageDeclaration().isPresent()
                        ? cu.getPackageDeclaration().get().getNameAsString()
                        : "";
                AtomicInteger nodeIndex = new AtomicInteger(0);

                classNodes.addAll(
                        cu.findAll(TypeDeclaration.class).stream()
                                .map(typeDecl -> new ContextDocument(
                                        new Document(typeDecl.toString()),
                                        new Context(
                                                source,
                                                nodeIndex.getAndIncrement(),
                                                "ClassNode::" + packageName + "." + typeDecl.getNameAsString(),
                                                typeDecl.toString(),
                                                ContextType.CODE)))

                                .toList());
            });
        } catch (IOException e) {
        }

        return classNodes;
    }
}
