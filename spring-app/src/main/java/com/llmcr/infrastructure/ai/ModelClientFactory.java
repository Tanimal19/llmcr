package com.llmcr.infrastructure.ai;

import com.google.genai.Client;
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

@Component
public class ModelClientFactory {

    @Value("${spring.ai.openai.base-url}")
    private String OPENAI_BASE_URL;

    @Value("${spring.ai.google.genai.api-key}")
    private String GEMINI_API_KEY;

    private static final String OPENAI_PROVIDER = "openai";

    private final OpenAiApi baseOpenAiApi;
    private final RetryTemplate retryTemplate;

    public ModelClientFactory(OpenAiApi baseOpenAiApi) {
        this.baseOpenAiApi = baseOpenAiApi;

        this.retryTemplate = RetryTemplate.builder().maxAttempts(3).build();
        this.retryTemplate.setBackOffPolicy(new LocalModelBackOffPolicy());
    }

    public ChatClient createChatClient(String provider, String model) {
        if (provider.equalsIgnoreCase("google")) {
            return ChatClient.builder(
                    GoogleGenAiChatModel.builder()
                            .genAiClient(Client.builder().apiKey(GEMINI_API_KEY).build())
                            .defaultOptions(GoogleGenAiChatOptions.builder().model(model).build())
                            .build())
                    .build();
        } else if (provider.equalsIgnoreCase(OPENAI_PROVIDER)) {
            return ChatClient.builder(
                    OpenAiChatModel.builder()
                            .openAiApi(baseOpenAiApi)
                            .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                            .retryTemplate(retryTemplate)
                            .build())
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported chat model provider: " + provider);
        }
    }

    public EmbeddingModel createEmbeddingModel(String provider, String model) {
        if (provider.equalsIgnoreCase(OPENAI_PROVIDER)) {
            return new OpenAiEmbeddingModel(
                    baseOpenAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder().model(model).build(),
                    retryTemplate);
        } else {
            throw new IllegalArgumentException("Unsupported embedding model provider: " + provider);
        }
    }

    public RerankingModel createRerankingModel(String provider, String model) {
        if (provider.equalsIgnoreCase(OPENAI_PROVIDER)) {
            return new OpenAiRerankingModel(new OpenAiRerankingApi(OPENAI_BASE_URL, "no-key"), model, retryTemplate);
        } else {
            throw new IllegalArgumentException("Unsupported reranking model provider: " + provider);
        }
    }
}
