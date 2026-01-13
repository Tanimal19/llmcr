package com.example.llmcr.extractor;

import com.example.llmcr.datasource.*;
import com.example.llmcr.entity.ClassNode;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Extracts ClassNode objects from various data sources.
 */
public class ClassNodeExtractor implements VoidDataSourceExtractor<List<ClassNode>> {

    public ClassNodeExtractor() {
    }

    @Override
    public List<ClassNode> visit(CompilationUnitSource source) {
        List<ClassNode> classNodes = new ArrayList<>();

        CompilationUnit cu = source.getCu();
        String packageName = cu.getPackageDeclaration().isPresent()
                ? cu.getPackageDeclaration().get().getNameAsString()
                : "";

        // Extract classes
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            UUID id = UUID.randomUUID();
            String signature = packageName + "." + classDecl.getNameAsString();
            String codeText = classDecl.toString();

            ClassNode classNode = new ClassNode(id, signature, codeText);
            classNodes.add(classNode);
        });

        // Extract enums
        cu.findAll(EnumDeclaration.class).forEach(enumDecl -> {
            UUID id = UUID.randomUUID();
            String signature = packageName + "." + enumDecl.getNameAsString();
            String codeText = enumDecl.toString();

            ClassNode classNode = new ClassNode(id, signature, codeText);
            classNodes.add(classNode);
        });

        // Extract records (Java 14+)
        cu.findAll(RecordDeclaration.class).forEach(recordDecl -> {
            UUID id = UUID.randomUUID();
            String signature = packageName + "." + recordDecl.getNameAsString();
            String codeText = recordDecl.toString();

            ClassNode classNode = new ClassNode(id, signature, codeText);
            classNodes.add(classNode);
        });

        System.out.println("Extracted " + classNodes.size() + " ClassNodes from CompilationUnitSource:"
                + cu.getStorage().get().getPath());

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
