package com.llmcr.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.llmcr.entity.ClassNode;
import com.llmcr.entity.Source;
import com.llmcr.entity.Source.SourceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts ClassNode objects from given Java project root
 */
public class ClassNodeExtractor implements ContextExtractor<ClassNode> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassNodeExtractor.class);
    private final JavaParser parser;

    public ClassNodeExtractor() {
        this.parser = new JavaParser();
    }

    public boolean supports(Source source) {
        return source.getSourceType() == SourceType.JAVACODE;
    }

    public List<ClassNode> extract(Source source) {

        if (source.getSourcePath().equals("package-info.java")) {
            LOGGER.info("Skipping non-class java source: " + source.getSourcePath());
            return List.of();
        }

        Path javaPath = Paths.get(source.getSourcePath());
        if (!Files.exists(javaPath) || !Files.isRegularFile(javaPath)) {
            LOGGER.warn("Source path does not exist or is not a regular file: " + javaPath);
            return List.of();
        }

        List<ClassNode> classNodes = new ArrayList<>();
        try {
            ParseResult<CompilationUnit> result = parser.parse(javaPath);
            result.getResult().ifPresent(cu -> {
                String packageName = cu.getPackageDeclaration().isPresent()
                        ? cu.getPackageDeclaration().get().getNameAsString()
                        : "";

                classNodes.addAll(
                        Stream.of(
                                cu.findAll(ClassOrInterfaceDeclaration.class),
                                cu.findAll(EnumDeclaration.class),
                                cu.findAll(RecordDeclaration.class))
                                .flatMap(List::stream)
                                .map(typeDecl -> {
                                    String signature = packageName + "." + typeDecl.getNameAsString();
                                    String code = typeDecl.toString();
                                    return new ClassNode(source, signature, code);
                                })
                                .collect(Collectors.toList()));
            });
        } catch (IOException e) {
            LOGGER.warn("Parse failed for " + javaPath + ": " + e.getMessage());
        }

        return classNodes;
    }

}
