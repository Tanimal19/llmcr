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
import com.example.llmcr.service.DataSourceFactoryService;
import com.example.llmcr.service.ETLService;

@SpringBootApplication(exclude = MariaDbStoreAutoConfiguration.class)
public class LlmcrApplication implements CommandLineRunner {

	@Autowired
	private DataStore dataStore;

	@Autowired
	private VectorStore vectorStore;

	@Autowired
	@Qualifier("googleGenAiChatModel")
	private ChatModel chatModel;

	@Autowired
	private DataSourceFactoryService dataSourceFactory;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(LlmcrApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		// String javaProjectRootPathString = "../_datasets/spring-ai-main simple";
		// List<String> documentPathStringList = List.of(
		// javaProjectRootPathString +
		// "/spring-ai-docs/src/main/antora/modules/ROOT/pages/", // javadocs
		// "../_datasets/Effective Java (2017, Addison-Wesley).pdf");

		// System.out.println("Creating data sources from paths...");
		// List<DataSource> dataSources = new ArrayList<>();
		// dataSources.addAll(dataSourceFactory.createFromJavaProjectPath(javaProjectRootPathString));
		// for (String documentPathString : documentPathStringList) {
		// dataSources.addAll(dataSourceFactory.createFromPath(documentPathString));
		// }

		new ETLService(dataStore, vectorStore, chatModel).transform();
	}
}
