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
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.llmcr.model.reranking.OpenAiRerankingApi;
import com.llmcr.model.reranking.OpenApiRerankingModel;

@Configuration
public class ModelClientConfig {

    @Autowired
    private OpenAiApi baseOpenAiApi;

    @Autowired
    private OpenAiChatModel baseOpenAiChatModel;

    // ── LargeChatClient ──────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "llmcr.chat.large.provider", havingValue = "openai", matchIfMissing = true)
    public LargeChatClient largeChatClientOpenAi(
            @Value("${llmcr.chat.large.openai.url}") String baseUrl,
            @Value("${llmcr.chat.large.model}") String model) {
        OpenAiApi openAiApi = baseOpenAiApi.mutate().baseUrl(baseUrl).build();
        OpenAiChatModel chatModel = baseOpenAiChatModel.mutate()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
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
            @Value("${llmcr.chat.small.openai.url}") String baseUrl,
            @Value("${llmcr.chat.small.model}") String model) {
        OpenAiApi openAiApi = baseOpenAiApi.mutate().baseUrl(baseUrl).build();
        OpenAiChatModel chatModel = baseOpenAiChatModel.mutate()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
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
            @Value("${llmcr.embedding.openai.url}") String baseUrl,
            @Value("${llmcr.embedding.model}") String model) {
        OpenAiApi openAiApi = baseOpenAiApi.mutate().baseUrl(baseUrl).build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
        return new EmbeddingClient(embeddingModel);
    }

    // ── RerankingClient ──────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "llmcr.reranking.provider", havingValue = "openai", matchIfMissing = true)
    public RerankingClient rerankingClientOpenAi(
            @Value("${llmcr.reranking.openai.url}") String baseUrl,
            @Value("${llmcr.reranking.model}") String defaultModel) {
        OpenAiRerankingApi rerankingApi = new OpenAiRerankingApi(baseUrl, "no-key");
        OpenApiRerankingModel rerankingModel = new OpenApiRerankingModel(rerankingApi, defaultModel,
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
        return new RerankingClient(rerankingModel);
    }
}
