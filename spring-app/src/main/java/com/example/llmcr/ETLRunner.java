package com.example.llmcr;

import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.llmcr.entity.Embedding.EmbeddingContentType;
import com.example.llmcr.repository.DataStore;
import com.example.llmcr.service.etl.DataSourceFactoryService;
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

        ExtractService e = new ExtractService(defaultDataStore);
        e.extract(
                DataSourceFactoryService.createFromJavaProject(javaProjectRootPathString),
                3000);
        e.extract(
                DataSourceFactoryService.createFromPath(javaDocPathString), 3000);

        TransformService t = new TransformService(defaultDataStore,
                defaultChatModel);
        // t.enrich(10);
        t.chunk(new TokenTextSplitter(500, 350, 100, 10000, true));

        // create three different index sets
        new LoadService(defaultDataStore,
                FaissVectorStoreFactory.create("full"))
                .load(Set.of(EmbeddingContentType.CODE, EmbeddingContentType.ENRICHMENT,
                        EmbeddingContentType.DOCUMENT));
        new LoadService(defaultDataStore,
                FaissVectorStoreFactory.create("enriched"))
                .load(Set.of(EmbeddingContentType.ENRICHMENT,
                        EmbeddingContentType.DOCUMENT));
        new LoadService(defaultDataStore,
                FaissVectorStoreFactory.create("plain"))
                .load(Set.of(EmbeddingContentType.CODE, EmbeddingContentType.DOCUMENT));

    }
}
