package com.llmcr.runner;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "parse")
public class SymbolParser implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> nonOptionArgs = args.getNonOptionArgs();
        if (nonOptionArgs.isEmpty()) {
            System.err.println("Usage: --app.mode=parse <project-root-dir> [output.txt]");
            return;
        }

        Path projectRoot = Path.of(nonOptionArgs.get(0)).toAbsolutePath().normalize();
        Path outputFile = nonOptionArgs.size() > 1
                ? Path.of(nonOptionArgs.get(1)).toAbsolutePath().normalize()
                : projectRoot.resolve("symbols.txt");

        if (!Files.isDirectory(projectRoot)) {
            System.err.println("Not a directory: " + projectRoot);
            return;
        }

        List<SymbolEntry> symbols = parseProject(projectRoot);
        writeSymbols(symbols, outputFile);

        System.out.printf(
                "Parsed %d symbols from %s%n -> %s%n", symbols.size(), projectRoot, outputFile);
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private List<SymbolEntry> parseProject(Path projectRoot) throws IOException {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        StaticJavaParser.setConfiguration(config);

        List<SymbolEntry> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(
                            javaFile -> {
                                try {
                                    CompilationUnit cu = StaticJavaParser.parse(javaFile);
                                    String packageName = cu.getPackageDeclaration()
                                            .map(pd -> pd.getNameAsString())
                                            .orElse("");
                                    cu.accept(new SymbolCollector(packageName, entries), null);
                                } catch (Exception e) {
                                    System.err.println("Skipping " + javaFile + ": " + e.getMessage());
                                }
                            });
        }
        return entries;
    }

    // -------------------------------------------------------------------------
    // Output
    // -------------------------------------------------------------------------

    private void writeSymbols(List<SymbolEntry> symbols, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8))) {

            String currentClass = null;
            for (SymbolEntry entry : symbols) {
                if (!entry.className().equals(currentClass)) {
                    if (currentClass != null)
                        writer.println();
                    writer.println("CLASS: " + entry.className());
                    currentClass = entry.className();
                }
                if (entry.methodName() != null) {
                    writer.println("  METHOD: " + entry.methodName());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Visitor
    // -------------------------------------------------------------------------

    private static class SymbolCollector extends VoidVisitorAdapter<Void> {

        private final List<SymbolEntry> entries;

        SymbolCollector(String packageName, List<SymbolEntry> entries) {
            this.entries = entries;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            String simpleName = n.getNameAsString();
            entries.add(new SymbolEntry(simpleName, null));

            for (MethodDeclaration method : n.getMethods()) {
                entries.add(new SymbolEntry(simpleName, method.getNameAsString()));
            }

            super.visit(n, arg);
        }

        @Override
        public void visit(EnumDeclaration n, Void arg) {
            String simpleName = n.getNameAsString();
            entries.add(new SymbolEntry(simpleName, null));

            for (MethodDeclaration method : n.getMethods()) {
                entries.add(new SymbolEntry(simpleName, method.getNameAsString()));
            }

            super.visit(n, arg);
        }
    }

    // -------------------------------------------------------------------------
    // Data
    // -------------------------------------------------------------------------

    private record SymbolEntry(String className, String methodName) {
    }
}
