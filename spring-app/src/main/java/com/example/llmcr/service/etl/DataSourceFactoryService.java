package com.example.llmcr.service.etl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.llmcr.datasource.AsciiDocSource;
import com.example.llmcr.datasource.CompilationUnitSource;
import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.datasource.MarkdownSource;
import com.example.llmcr.datasource.PdfSource;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

public final class DataSourceFactoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceFactoryService.class);

    private DataSourceFactoryService() {
    }

    public static List<DataSource> createFromJavaProject(String javaProjectRootPathString) {
        List<DataSource> dataSources = new ArrayList<>();

        Path javaProjectRoot = Paths.get(javaProjectRootPathString);
        if (!Files.exists(javaProjectRoot) || !Files.isDirectory(javaProjectRoot)) {
            LOGGER.warn("Java project root path does not exist or is not a directory: " + javaProjectRoot);
            return dataSources;
        }

        JavaParser parser = new JavaParser();
        try (Stream<Path> paths = Files.walk(javaProjectRoot)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java")
                            && !p.getFileName().toString().equals("package-info.java"))
                    .forEach(p -> {
                        try {
                            ParseResult<CompilationUnit> result = parser.parse(p);
                            result.getResult().ifPresent(cu -> dataSources.add(new CompilationUnitSource(cu)));
                        } catch (IOException e) {
                            LOGGER.warn("Parse failed for " + p + ": " + e.getMessage());
                        }
                    });

        } catch (IOException e) {
            LOGGER.warn("Error walking Java project path: " + e.getMessage());
        }

        LOGGER.info(
                "Parsed " + dataSources.size() + " Java sources from project path: "
                        + javaProjectRootPathString);
        return dataSources;
    }

    public static List<DataSource> createFromPath(String sourcePathString) {
        Path sourcePath = Paths.get(sourcePathString);
        if (!Files.exists(sourcePath)) {
            LOGGER.warn("Source path does not exist: " + sourcePath);
            return new ArrayList<>();
        }

        if (Files.isDirectory(sourcePath)) {
            return createFromDirectory(sourcePath);
        } else {
            return List.of(createFromFile(sourcePath));
        }
    }

    private static List<DataSource> createFromDirectory(Path directoryPath) {
        List<DataSource> dataSources = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(directoryPath)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());

            for (Path filePath : files) {
                dataSources.add(createFromFile(filePath));
            }
        } catch (IOException e) {
            LOGGER.warn("Error walking directory path: " + e.getMessage());
        }

        LOGGER.info("Parsed " + dataSources.size() + " document sources from directory: " + directoryPath);
        return dataSources;
    }

    private static DataSource createFromFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".md")) {
            return new MarkdownSource(filePath);
        } else if (fileName.endsWith(".adoc")) {
            return new AsciiDocSource(filePath);
        } else if (fileName.endsWith(".pdf")) {
            return new PdfSource(filePath);
        } else {
            LOGGER.warn("Unsupported file type for data source: " + filePath);
            return null;
        }
    }
}
