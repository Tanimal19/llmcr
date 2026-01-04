package com.example.llmcr.extractor;

import com.example.llmcr.datasource.*;
import com.example.llmcr.entity.ClassNode;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Extracts ClassNode objects from various data sources.
 */
@Component
public class ClassNodeExtractor implements VoidDataSourceExtractor<List<ClassNode>> {

    @Override
    public List<ClassNode> visit(CompilationUnitSource source) {
        List<ClassNode> classNodes = new ArrayList<>();

        // Extract classes
        source.getCu().findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            String id = UUID.randomUUID().toString();
            String signature = classDecl.getNameAsString();
            String codeText = classDecl.toString();

            ClassNode classNode = new ClassNode(id, signature, codeText);
            classNodes.add(classNode);
        });

        // Extract enums
        source.getCu().findAll(EnumDeclaration.class).forEach(enumDecl -> {
            String id = UUID.randomUUID().toString();
            String signature = enumDecl.getNameAsString();
            String codeText = enumDecl.toString();

            ClassNode classNode = new ClassNode(id, signature, codeText);
            classNodes.add(classNode);
        });

        // Extract records (Java 14+)
        source.getCu().findAll(RecordDeclaration.class).forEach(recordDecl -> {
            String id = UUID.randomUUID().toString();
            String signature = recordDecl.getNameAsString();
            String codeText = recordDecl.toString();

            ClassNode classNode = new ClassNode(id, signature, codeText);
            classNodes.add(classNode);
        });
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
