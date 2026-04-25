package com.llmcr.service.etl.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.llmcr.entity.Source;
import com.llmcr.entity.Source.SourceType;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
@Component
public class ClassNodeExtractor implements SourceExtractor {

    private static final Logger log = LoggerFactory.getLogger(ClassNodeExtractor.class);

    private final JavaParser parser;

    public ClassNodeExtractor() {
        this.parser = new JavaParser();
    }

    @Override
    public boolean supports(Source source) {
        return source.getType() == SourceType.JAVACODE;
    }

    @Override
    public List<Context> apply(Source source) {

        if (source.getPath().equals("package-info.java")) {
            return List.of();
        }

        Path javaPath = Paths.get(source.getPath());
        if (!Files.exists(javaPath) || !Files.isRegularFile(javaPath)) {
            return List.of();
        }

        List<Context> classNodes = new ArrayList<>();
        try {
            ParseResult<CompilationUnit> result = parser.parse(javaPath);
            result.getResult().ifPresent(cu -> {
                String packageName = cu.getPackageDeclaration().isPresent()
                        ? cu.getPackageDeclaration().get().getNameAsString()
                        : "";
                AtomicInteger nodeIndex = new AtomicInteger(0);

                classNodes.addAll(
                        cu.findAll(TypeDeclaration.class).stream()
                                .map(typeDecl -> {
                                    int currentIndex = nodeIndex.getAndIncrement();
                                    String qualifiedTypeName = buildQualifiedTypeName(packageName, typeDecl);

                                    log.info("[ClassNodeExtractor] source={} typeDecl={} contextIndex={}",
                                            source.getPath(), qualifiedTypeName, currentIndex);

                                    return new Context(
                                            source,
                                            currentIndex,
                                            "ClassNode::" + qualifiedTypeName,
                                            typeDecl.toString(),
                                            ContextType.CLASSNODE);
                                })
                                .toList());
            });
        } catch (IOException e) {
        }

        return classNodes;
    }

    private String buildQualifiedTypeName(String packageName, TypeDeclaration<?> typeDecl) {
        List<String> typeNameParts = new ArrayList<>();
        Node currentNode = typeDecl;

        while (currentNode != null) {
            if (currentNode instanceof TypeDeclaration<?> currentType) {
                typeNameParts.add(0, currentType.getNameAsString());
            }
            currentNode = currentNode.getParentNode().orElse(null);
        }

        String nestedTypePath = String.join(".", typeNameParts);
        return packageName.isBlank() ? nestedTypePath : packageName + "." + nestedTypePath;
    }
}
