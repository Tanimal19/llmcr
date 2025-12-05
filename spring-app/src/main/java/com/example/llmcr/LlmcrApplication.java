package com.example.llmcr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.llmcr.datasource.AsciiDocSource;
import com.example.llmcr.datasource.CompilationUnitSource;
import com.example.llmcr.datasource.MarkdownSource;
import com.example.llmcr.datasource.PdfSource;
import com.example.llmcr.datasource.RawDataSource;
import com.example.llmcr.repository.DataStore;
import com.example.llmcr.service.ETLService;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.SourceRoot;

@SpringBootApplication
public class LlmcrApplication implements CommandLineRunner {

	@Autowired
	private DataStore dataStore;

	@Autowired
	private VectorStore vectorStore;

	@Autowired
	private ChatModel chatModel;

	@Value("${spring.ai.google.genai.api-key}")
	private String apiKey;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(LlmcrApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		// Define data source paths
		String javaProjectRootPathString = "../_datasets/spring-ai-main";
		List<String> documentPathStringList = List.of(
				"../_datasets/spring-ai-main/spring-ai-docs/src/main/antora/modules/ROOT/pages/");

		List<RawDataSource> dataSources = new ArrayList<>();

		// Convert Java project path to CompilationUnitSource instances
		Path javaProjectRoot = Paths.get(javaProjectRootPathString);
		if (Files.exists(javaProjectRoot) && Files.isDirectory(javaProjectRoot)) {
			System.out.println("Parsing Java project from: " + javaProjectRoot);
			try {
				SourceRoot sourceRoot = new SourceRoot(javaProjectRoot);
				List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();

				for (ParseResult<CompilationUnit> result : parseResults) {
					if (result.isSuccessful() && result.getResult().isPresent()) {
						dataSources.add(new CompilationUnitSource(result.getResult().get()));
					}
				}
				System.out.println("Parsed " + dataSources.size() + " compilation units");
			} catch (IOException e) {
				System.err.println("Error parsing Java project: " + e.getMessage());
			}
		} else {
			System.out.println("Java project path does not exist or is not a directory: " + javaProjectRoot);
		}

		// Convert document paths to MarkdownSource instances
		for (String docPathString : documentPathStringList) {
			Path docPath = Paths.get(docPathString);
			if (Files.exists(docPath)) {
				System.out.println("Processing documents from: " + docPath);
				try (Stream<Path> paths = Files.walk(docPath)) {
					List<Path> docFiles = paths
							.filter(Files::isRegularFile)
							.collect(Collectors.toList());

					for (Path filePath : docFiles) {
						String fileName = filePath.getFileName().toString().toLowerCase();
						if (fileName.endsWith(".md")) {
							dataSources.add(new MarkdownSource(filePath));
						} else if (fileName.endsWith(".adoc") || fileName.endsWith(".asciidoc")) {
							dataSources.add(new AsciiDocSource(filePath));
						} else if (fileName.endsWith(".pdf")) {
							dataSources.add(new PdfSource(filePath));
						}
					}
					System.out.println("Added " + docFiles.size() + " document sources");

				} catch (IOException e) {
					System.err.println("Error reading documents: " + e.getMessage());
				}
			} else {
				System.out.println("Document path does not exist: " + docPath);
			}
		}

		// run ETL pipeline
		new ETLService(dataSources, dataStore, vectorStore, chatModel).extract().transform().load();

	}
}
