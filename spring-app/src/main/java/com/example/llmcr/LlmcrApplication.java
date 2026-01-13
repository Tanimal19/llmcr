package com.example.llmcr;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.llmcr.repository.DataStore;
import com.example.llmcr.repository.FaissVectorStore;
import com.example.llmcr.service.FaissService;
import com.example.llmcr.utils.DataSourceFactoryUtils;

@SpringBootApplication()
public class LlmcrApplication implements CommandLineRunner {

	@Autowired
	private DataStore dataStore;

	@Autowired
	private ChatModel chatModel;

	@Autowired
	private FaissVectorStore vectorStore;

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
		new ETLPipeline(dataStore, chatModel, vectorStore)
				.extract(DataSourceFactoryUtils.createFromJavaProject(javaProjectRootPathString))
				.extract(DataSourceFactoryUtils.createFromPath(javaDocPathString))
				.transform();
	}
}
