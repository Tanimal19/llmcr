package com.llmcr.infrastructure.ai;

import com.google.genai.Client;
import com.llmcr.config.SystemConfig.ModelConfig;
import com.llmcr.infrastructure.ai.reranking.OpenAiRerankingApi;
import com.llmcr.infrastructure.ai.reranking.OpenAiRerankingModel;
import com.llmcr.infrastructure.ai.reranking.RerankingModel;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * A custom model factory to allow initialization of multiple providers and
 * model types. We do not rely on Spring AI's auto-configuration and model
 * factory because it does not support multiple providers
 * co-existing in the same application context.
 */
@Component
public class ModelClientFactory {

    private static final String GOOGLE_PROVIDER_NAME = "google";
    private static final String OPENAI_PROVIDER_NAME = "openai";

    @Value("${spring.ai.openai.base-url}")
    private String openAiBaseUrl;

    @Value("${spring.ai.google.genai.api-key}")
    private String geminiApiKey;

    private final OpenAiApi baseOpenAiApi;
    private final RetryTemplate retryTemplate;

    public ModelClientFactory(OpenAiApi baseOpenAiApi) {
        this.baseOpenAiApi = baseOpenAiApi;
        this.retryTemplate = RetryTemplate.builder().maxAttempts(3).build();
        this.retryTemplate.setBackOffPolicy(new LocalModelBackOffPolicy());
    }

    public ChatClient createChatClient(ModelConfig config) {
        if (config.provider().equalsIgnoreCase(GOOGLE_PROVIDER_NAME)) {
            return ChatClient.builder(
                    GoogleGenAiChatModel.builder()
                            .genAiClient(Client.builder().apiKey(geminiApiKey).build())
                            .defaultOptions(GoogleGenAiChatOptions.builder().model(config.name()).build())
                            .build())
                    .build();
        } else if (config.provider().equalsIgnoreCase(OPENAI_PROVIDER_NAME)) {
            return ChatClient.builder(
                    OpenAiChatModel.builder()
                            .openAiApi(baseOpenAiApi)
                            .defaultOptions(OpenAiChatOptions.builder().model(config.name()).build())
                            .retryTemplate(retryTemplate)
                            .build())
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported chat model provider: " + config.provider());
        }
    }

    public EmbeddingModel createEmbeddingModel(ModelConfig config) {
        if (config.provider().equalsIgnoreCase(GOOGLE_PROVIDER_NAME)) {
            return new OpenAiEmbeddingModel(
                    baseOpenAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder().model(config.name()).build(),
                    retryTemplate);
        } else {
            throw new IllegalArgumentException("Unsupported embedding model provider: " + config.provider());
        }
    }

    public RerankingModel createRerankingModel(ModelConfig config) {
        if (config.provider().equalsIgnoreCase(GOOGLE_PROVIDER_NAME)) {
            return new OpenAiRerankingModel(new OpenAiRerankingApi(openAiBaseUrl), config.name(), retryTemplate);
        } else {
            throw new IllegalArgumentException("Unsupported reranking model provider: " + config.provider());
        }
    }
}
