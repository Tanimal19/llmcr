package com.example.llmcr.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.example.llmcr.datasource.AsciiDocSource;
import com.example.llmcr.datasource.CompilationUnitSource;
import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.datasource.MarkdownSource;
import com.example.llmcr.datasource.PdfSource;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.SourceRoot;

/**
 * Factory to create DataSource instances from various file types and
 * directories.
 */
@Service
public class DataSourceFactoryService {
    public List<DataSource> createFromJavaProjectPath(String javaProjectRootPathString) {
        List<DataSource> dataSources = new ArrayList<>();

        Path javaProjectRoot = Paths.get(javaProjectRootPathString);
        if (Files.exists(javaProjectRoot) && Files.isDirectory(javaProjectRoot)) {
            try {
                SourceRoot sourceRoot = new SourceRoot(javaProjectRoot);
                List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();

                for (ParseResult<CompilationUnit> result : parseResults) {
                    if (result.isSuccessful() && result.getResult().isPresent()) {
                        dataSources.add(new CompilationUnitSource(result.getResult().get()));
                    }
                }
                System.out.println(
                        "Parsed " + dataSources.size() + " compilation units from Java project: " + javaProjectRoot);
            } catch (IOException e) {
                System.err.println("Error parsing Java project: " + e.getMessage());
            }
        } else {
            System.out.println("Java project path does not exist or is not a directory: " + javaProjectRoot);
        }

        return dataSources;
    }

    public List<DataSource> createFromPath(String sourcePathString) {
        Path sourcePath = Paths.get(sourcePathString);
        if (!Files.exists(sourcePath)) {
            System.out.println("Source path does not exist: " + sourcePath);
            return new ArrayList<>();
        }

        if (Files.isDirectory(sourcePath)) {
            return createFromDirectory(sourcePath);
        } else {
            return List.of(createFromFile(sourcePath));
        }
    }

    public List<DataSource> createFromDirectory(Path directoryPath) {
        List<DataSource> dataSources = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(directoryPath)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());

            for (Path filePath : files) {
                dataSources.add(createFromFile(filePath));
            }
            System.out.println("Parsed " + dataSources.size() + " document sources from directory: " + directoryPath);
        } catch (IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
        }

        return dataSources;
    }

    public DataSource createFromFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".md")) {
            return new MarkdownSource(filePath);
        } else if (fileName.endsWith(".adoc")) {
            return new AsciiDocSource(filePath);
        } else if (fileName.endsWith(".pdf")) {
            return new PdfSource(filePath);
        }

        System.out.println("Unsupported file type: " + filePath);
        return null;
    }
}
