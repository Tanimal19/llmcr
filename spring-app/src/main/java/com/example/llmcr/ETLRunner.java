package com.example.llmcr;

import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.llmcr.entity.Chunk.ChunkContentType;
import com.example.llmcr.service.etl.DataSourceFactoryService;
import com.example.llmcr.service.etl.DataStore;
import com.example.llmcr.service.etl.ExtractService;
import com.example.llmcr.service.etl.LoadService;
import com.example.llmcr.service.etl.TransformService;
import com.example.llmcr.service.faiss.FaissVectorStoreFactory;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "etl")
public class ETLRunner implements CommandLineRunner {
    @Autowired
    private DataStore defaultDataStore;

    @Autowired
    private ChatModel defaultChatModel;

    @Autowired
    private FaissVectorStoreFactory FaissVectorStoreFactory;

    @Override
    public void run(String... args) throws Exception {
        String javaProjectRootPathString = "../_datasets/spring-ai-main simple";
        String javaDocPathString = javaProjectRootPathString +
                "/spring-ai-docs/src/main/antora/modules/ROOT/pages/";

        ExtractService.extract(
                defaultDataStore,
                DataSourceFactoryService.createFromJavaProject(javaProjectRootPathString),
                3000);
        ExtractService.extract(
                defaultDataStore,
                DataSourceFactoryService.createFromPath(javaDocPathString), 3000);

        TransformService.enrich(defaultDataStore, defaultChatModel, 10);

        LoadService.chunk(defaultDataStore,
                new TokenTextSplitter());
        LoadService.load(defaultDataStore, FaissVectorStoreFactory.create("full"),
                Set.of(ChunkContentType.CODE, ChunkContentType.ENRICHMENT,
                        ChunkContentType.DOCUMENT));
        LoadService.load(defaultDataStore, FaissVectorStoreFactory.create("enriched"),
                Set.of(ChunkContentType.ENRICHMENT, ChunkContentType.DOCUMENT));
        LoadService.load(defaultDataStore, FaissVectorStoreFactory.create("plain"),
                Set.of(ChunkContentType.CODE, ChunkContentType.DOCUMENT));
    }
}
