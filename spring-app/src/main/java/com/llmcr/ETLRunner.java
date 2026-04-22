package com.llmcr;

import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Chunk.ChunkContentType;
import com.llmcr.etl.step.ExtractStep;
import com.llmcr.service.ChunkService;
import com.llmcr.service.TransformService;
import com.llmcr.service.etl.DataSourceFactoryService;
import com.llmcr.service.vectorstore.faiss.FaissVectorStoreFactory;
import com.llmcr.storage.DataStore;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "etl")
public class ETLRunner implements CommandLineRunner {
	@Autowired
	private DataStore defaultDataStore;

	@Autowired
	private ChatModel defaultChatModel;

	@Autowired
	private FaissVectorStoreFactory FaissVectorStoreFactory;

	@Value("${etl.input.javaproject.path}")
	private String javaProjectRootPathString;

	@Value("${etl.input.document.paths}")
	private List<String> javaDocPathStrings;

	@Override
	public void run(String... args) throws Exception {
		ExtractStep.extract(
				defaultDataStore,
				DataSourceFactoryService.createFromJavaProject(javaProjectRootPathString),
				3000);
		for (String javaDocPathString : javaDocPathStrings) {
			ExtractStep.extract(
					defaultDataStore,
					DataSourceFactoryService.createFromPath(javaDocPathString), 3000);
		}

		TransformService.enrich(defaultDataStore, defaultChatModel, 10);

		ChunkService.chunk(defaultDataStore,
				new TokenTextSplitter());
		ChunkService.load(defaultDataStore, FaissVectorStoreFactory.create("full"),
				Set.of(ChunkContentType.CLASSNODE, ChunkContentType.ENRICHMENT, ChunkContentType.DOCUMENT));
	}
}
