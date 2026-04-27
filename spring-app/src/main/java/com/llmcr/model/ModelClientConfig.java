package com.llmcr.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import com.llmcr.model.reranking.OpenAiRerankingApi;
import com.llmcr.model.reranking.OpenApiRerankingModel;

@Configuration
public class ModelClientConfig {

    @Value("${spring.ai.openai.base-url}")
    private String openAiBaseUrl;

    private RetryTemplate defaultRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(10000)
                .build();
    }

    // ── LargeChatClient ──────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "llmcr.chat.large.provider", havingValue = "openai", matchIfMissing = true)
    public LargeChatClient largeChatClientOpenAi(
            OpenAiChatModel baseOpenAiChatModel,
            @Value("${llmcr.chat.large.model}") String model) {
        OpenAiChatModel chatModel = baseOpenAiChatModel.mutate()
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .retryTemplate(defaultRetryTemplate())
                .build();
        return new LargeChatClient(ChatClient.create(chatModel));
    }

    @Bean
    @ConditionalOnProperty(name = "llmcr.chat.large.provider", havingValue = "google.genai")
    public LargeChatClient largeChatClientGoogleGenAi(
            GoogleGenAiChatModel googleGenAiChatModel,
            @Value("${llmcr.chat.large.model}") String model) {
        ChatClient chatClient = ChatClient.builder(googleGenAiChatModel)
                .defaultOptions(GoogleGenAiChatOptions.builder().model(model).build())
                .build();
        return new LargeChatClient(chatClient);
    }

    // ── SmallChatClient ──────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "llmcr.chat.small.provider", havingValue = "openai", matchIfMissing = true)
    public SmallChatClient smallChatClientOpenAi(
            OpenAiChatModel baseOpenAiChatModel,
            @Value("${llmcr.chat.small.model}") String model) {
        OpenAiChatModel chatModel = baseOpenAiChatModel.mutate()
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .retryTemplate(defaultRetryTemplate())
                .build();
        return new SmallChatClient(ChatClient.create(chatModel));
    }

    @Bean
    @ConditionalOnProperty(name = "llmcr.chat.small.provider", havingValue = "google.genai")
    public SmallChatClient smallChatClientGoogleGenAi(
            GoogleGenAiChatModel googleGenAiChatModel,
            @Value("${llmcr.chat.small.model}") String model) {
        ChatClient chatClient = ChatClient.builder(googleGenAiChatModel)
                .defaultOptions(GoogleGenAiChatOptions.builder().model(model).build())
                .build();
        return new SmallChatClient(chatClient);
    }

    // ── EmbeddingClient ──────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "llmcr.embedding.provider", havingValue = "openai", matchIfMissing = true)
    public EmbeddingClient embeddingClientOpenAi(
            OpenAiApi baseOpenAiApi,
            @Value("${llmcr.embedding.model}") String model) {
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                baseOpenAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build(),
                defaultRetryTemplate());
        return new EmbeddingClient(embeddingModel);
    }

    // ── RerankingClient ──────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "llmcr.reranking.provider", havingValue = "openai", matchIfMissing = true)
    public RerankingClient rerankingClientOpenAi(
            @Value("${llmcr.reranking.model}") String model) {
        OpenAiRerankingApi rerankingApi = new OpenAiRerankingApi(openAiBaseUrl, "no-key");
        OpenApiRerankingModel rerankingModel = new OpenApiRerankingModel(rerankingApi, model,
                defaultRetryTemplate());
        return new RerankingClient(rerankingModel);
    }

}
