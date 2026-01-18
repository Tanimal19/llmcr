package com.example.llmcr;

import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.llmcr.entity.Chunk.ChunkType;
import com.example.llmcr.faiss.FaissVectorStore;
import com.example.llmcr.faiss.FaissVectorStoreFactory;
import com.example.llmcr.repository.DataStore;
import com.example.llmcr.service.etl.DataSourceFactoryService;
import com.example.llmcr.service.etl.ExtractService;
import com.example.llmcr.service.etl.LoadService;
import com.example.llmcr.service.etl.TransformService;

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

        FaissVectorStore enrichFaiss = FaissVectorStoreFactory.create("enriched");
        FaissVectorStore plainFaiss = FaissVectorStoreFactory.create("plain");

        ExtractService e = new ExtractService(defaultDataStore);
        e.extract(DataSourceFactoryService.createFromJavaProject(javaProjectRootPathString),
                3000);
        e.extract(DataSourceFactoryService.createFromPath(javaDocPathString), 3000);

        TransformService t = new TransformService(defaultDataStore,
                defaultChatModel);
        t.enrich(10, 2);
        t.chunk(new TokenTextSplitter(500, 350, 100, 10000, true));

        LoadService l_enrich = new LoadService(defaultDataStore, enrichFaiss);
        l_enrich.load(Set.of(ChunkType.CODE, ChunkType.SUMMARY, ChunkType.PARAGRAPH));

        LoadService l_plain = new LoadService(defaultDataStore, plainFaiss);
        l_plain.load(Set.of(ChunkType.CODE, ChunkType.PARAGRAPH));
    }
}
