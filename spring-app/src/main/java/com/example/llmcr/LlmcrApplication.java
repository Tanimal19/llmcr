package com.example.llmcr;

import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.llmcr.entity.Chunk.ChunkType;
import com.example.llmcr.faiss.FaissVectorStore;
import com.example.llmcr.faiss.FaissVectorStoreFactory;
import com.example.llmcr.repository.DataStore;
import com.example.llmcr.service.etl.DataSourceFactoryService;
import com.example.llmcr.service.etl.ExtractService;
import com.example.llmcr.service.etl.LoadService;
import com.example.llmcr.service.etl.TransformService;
import com.example.llmcr.service.rag.RAGService;
import com.example.llmcr.service.rag.augmentation.AnswerQueryPromptBuilder;
import com.example.llmcr.service.rag.retrieval.AdaptiveKStrategy;

@SpringBootApplication()
public class LlmcrApplication implements CommandLineRunner {

	@Autowired
	private DataStore defaultDataStore;

	@Autowired
	private ChatModel defaultChatModel;

	@Autowired
	private FaissVectorStoreFactory FaissVectorStoreFactory;

	@Autowired
	private EvaluationRunner evaluationRunner;

	@Value("${app.mode}")
	private String mode;

	public static void main(String[] args) {
		// to avoid starting web server
		SpringApplication app = new SpringApplication(LlmcrApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		switch (mode.toLowerCase()) {
			case "etl" -> runETL();
			case "rag" -> runRAG();
			case "evaluation" -> evaluationRunner.runAllGroups();
			default -> throw new IllegalArgumentException(
					"Unknown app.mode: " + mode);
		}
	}

	private void runETL() {
		// run a predefined ETL pipeline

		String javaProjectRootPathString = "../_datasets/spring-ai-main simple";
		String javaDocPathString = javaProjectRootPathString +
				"/spring-ai-docs/src/main/antora/modules/ROOT/pages/";

		FaissVectorStore enrichFaiss = FaissVectorStoreFactory.create("enrich");
		FaissVectorStore plainFaiss = FaissVectorStoreFactory.create("plain");

		ExtractService e = new ExtractService(defaultDataStore);
		e.extract(DataSourceFactoryService.createFromJavaProject(javaProjectRootPathString), 3000);
		e.extract(DataSourceFactoryService.createFromPath(javaDocPathString), 3000);

		TransformService t = new TransformService(defaultDataStore, defaultChatModel);
		t.enrich(10, 2);
		t.chunk(new TokenTextSplitter(500, 350, 100, 10000, true));

		LoadService l_enrich = new LoadService(defaultDataStore, enrichFaiss);
		l_enrich.load(Set.of(ChunkType.CODE, ChunkType.SUMMARY, ChunkType.PARAGRAPH));

		LoadService l_plain = new LoadService(defaultDataStore, plainFaiss);
		l_plain.load(Set.of(ChunkType.CODE, ChunkType.PARAGRAPH));
	}

	private void runRAG() {
		FaissVectorStore defaultFaiss = FaissVectorStoreFactory.create("enrich");
		RAGService r = new RAGService(defaultChatModel, defaultFaiss);
		r.setStrategy(new AdaptiveKStrategy());
		r.setPromptBuilder(new AnswerQueryPromptBuilder());

		// get query from console
		boolean isExit = false;
		Scanner scanner = new Scanner(System.in);
		while (!isExit) {
			System.out.print("Enter your query (or 'exit' to quit): ");
			String query = scanner.nextLine();
			if (query.equalsIgnoreCase("exit")) {
				isExit = true;
				scanner.close();
				System.out.println("Exiting...");
				break;
			}

			Map<String, Object> reponse = r.generation(query);
			System.out.println("Response: " + reponse.get("response"));
		}
	}
}
