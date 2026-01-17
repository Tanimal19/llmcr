package com.example.llmcr;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.llmcr.service.ETLPipeline;
import com.example.llmcr.service.RAGService;
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

		etlPipeline
				// .extract(DataSourceFactoryUtils.createFromJavaProject(javaProjectRootPathString))
				// .extract(DataSourceFactoryUtils.createFromPath(javaDocPathString))
				// .transform()
				.load();
	}
}
