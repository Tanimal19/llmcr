package com.example.llmcr;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.repository.DataStore;
import com.example.llmcr.utils.DataSourceFactoryUtils;

@SpringBootApplication(exclude = MariaDbStoreAutoConfiguration.class)
public class LlmcrApplication implements CommandLineRunner {

	@Autowired
	private DataStore dataStore;

	@Autowired
	private VectorStore vectorStore;

	@Autowired
	@Qualifier("googleGenAiChatModel")
	private ChatModel chatModel;

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

		long startTime = System.currentTimeMillis();
		System.out.println("+ Creating data sources from paths...");

		List<DataSource> dataSources = new ArrayList<>();
		dataSources.addAll(DataSourceFactoryUtils.createFromJavaProject(javaProjectRootPathString));
		dataSources.addAll(DataSourceFactoryUtils.createFromPath(javaDocPathString));

		long endTime = System.currentTimeMillis();
		System.out.println("+ Data source parsing completed in " + (endTime - startTime) + "ms");

		new ETLPipeline(dataStore, vectorStore, chatModel).extract(dataSources).transform().load();
	}
}
