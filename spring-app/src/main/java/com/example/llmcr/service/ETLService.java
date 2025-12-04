package com.example.llmcr.service;

import com.example.llmcr.datasource.*;
import com.example.llmcr.pipeline.ETLPipeline;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.stream.Stream;

/**
 * Service class that demonstrates how to use the ETL pipeline.
 */
@Service
public class ETLService {

    private final ETLPipeline etlPipeline;
    private final JavaParser javaParser;

    @Autowired
    public ETLService(ETLPipeline etlPipeline) {
        this.etlPipeline = etlPipeline;
        this.javaParser = new JavaParser();
    }

    /**
     * Process Java source files from a directory.
     */
    public void processJavaFiles(String directoryPath) {
        try {
            Path dir = Paths.get(directoryPath);
            if (!Files.exists(dir)) {
                System.err.println("Directory does not exist: " + directoryPath);
                return;
            }

            try (Stream<Path> paths = Files.walk(dir)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .forEach(this::addJavaFileToETL);
            }

            // Execute the ETL pipeline
            etlPipeline.execute();

        } catch (Exception e) {
            System.err.println("Error processing Java files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process documentation files from a directory.
     */
    public void processDocumentationFiles(String directoryPath) {
        try {
            Path dir = Paths.get(directoryPath);
            if (!Files.exists(dir)) {
                System.err.println("Directory does not exist: " + directoryPath);
                return;
            }

            try (Stream<Path> paths = Files.walk(dir)) {
                paths.forEach(path -> {
                    String fileName = path.toString().toLowerCase();
                    if (fileName.endsWith(".md")) {
                        etlPipeline.addRawDataSource(new MarkdownSource(path.toString()));
                    } else if (fileName.endsWith(".adoc") || fileName.endsWith(".asciidoc")) {
                        etlPipeline.addRawDataSource(new AsciiDocSource(path.toString()));
                    } else if (fileName.endsWith(".pdf")) {
                        etlPipeline.addRawDataSource(new PdfSource(path.toString()));
                    }
                });
            }

        } catch (Exception e) {
            System.err.println("Error processing documentation files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Add a single Java file to the ETL pipeline.
     */
    private void addJavaFileToETL(Path javaFilePath) {
        try {
            String content = Files.readString(javaFilePath);
            CompilationUnit cu = javaParser.parse(content).getResult().orElse(null);

            if (cu != null) {
                etlPipeline.addRawDataSource(new CompilationUnitSource(cu));
            }

        } catch (Exception e) {
            System.err.println("Error parsing Java file " + javaFilePath + ": " + e.getMessage());
        }
    }

    /**
     * Process a single file based on its extension.
     */
    public void processFile(String filePath) {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            System.err.println("File does not exist: " + filePath);
            return;
        }

        String fileName = path.toString().toLowerCase();

        if (fileName.endsWith(".java")) {
            addJavaFileToETL(path);
        } else if (fileName.endsWith(".md")) {
            etlPipeline.addRawDataSource(new MarkdownSource(filePath));
        } else if (fileName.endsWith(".adoc") || fileName.endsWith(".asciidoc")) {
            etlPipeline.addRawDataSource(new AsciiDocSource(filePath));
        } else if (fileName.endsWith(".pdf")) {
            etlPipeline.addRawDataSource(new PdfSource(filePath));
        } else {
            System.out.println("Unsupported file type: " + filePath);
        }
    }
}
