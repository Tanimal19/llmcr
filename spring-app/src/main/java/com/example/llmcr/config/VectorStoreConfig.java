package com.example.llmcr.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
            @Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel) {
        return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .build();
    }
}
