package com.example.llmcr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.llmcr.service.ETLPipeline;
import com.example.llmcr.service.RAGService;
import com.example.llmcr.service.rag.strategy.AdaptiveKStrategy;
import com.example.llmcr.service.rag.strategy.BaseRAGStrategy;
import com.example.llmcr.utils.DataSourceFactoryUtils;

@SpringBootApplication()
public class LlmcrApplication implements CommandLineRunner {

	@Autowired
	private ETLPipeline etlPipeline;

	@Autowired
	private RAGService ragService;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(LlmcrApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		String javaProjectRootPathString = "../_datasets/spring-ai-main simple";
		String javaDocPathString = javaProjectRootPathString +
				"/spring-ai-docs/src/main/antora/modules/ROOT/pages/";

		// ETL pipeline
		// etlPipeline
		// .extract(DataSourceFactoryUtils.createFromJavaProject(javaProjectRootPathString))
		// .extract(DataSourceFactoryUtils.createFromPath(javaDocPathString))
		// .transform()
		// .load();

		// RAG query
		String query = readStringFromFile("query.txt");
		ragService.setStrategy(new AdaptiveKStrategy());
		String answer = ragService.answerQuery(query);
		System.out.println("Answer: " + answer);
	}

	public String readStringFromFile(String filePath) {
		try {
			return Files.readString(Paths.get(filePath));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read file: " + filePath, e);
		}
	}
}
