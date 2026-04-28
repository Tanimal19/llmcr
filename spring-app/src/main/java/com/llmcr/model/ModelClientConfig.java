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
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.llmcr.model.reranking.OpenAiRerankingApi;
import com.llmcr.model.reranking.OpenApiRerankingModel;

@Configuration
public class ModelClientConfig {

    private static final long DEFAULT_BACKOFF_MILLIS = 10_000L;
    private static final long HTTP_502_503_BACKOFF_MILLIS = 30_000L;

    @Value("${spring.ai.openai.base-url}")
    private String openAiBaseUrl;

    private RetryTemplate defaultRetryTemplate() {
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .build();

        retryTemplate.setBackOffPolicy(new HttpStatusAwareBackOffPolicy(
                DEFAULT_BACKOFF_MILLIS,
                HTTP_502_503_BACKOFF_MILLIS));

        return retryTemplate;
    }

    private static final class HttpStatusAwareBackOffPolicy implements BackOffPolicy {

        private final long defaultBackoffMillis;
        private final long http502503BackoffMillis;

        private HttpStatusAwareBackOffPolicy(long defaultBackoffMillis, long http502503BackoffMillis) {
            this.defaultBackoffMillis = defaultBackoffMillis;
            this.http502503BackoffMillis = http502503BackoffMillis;
        }

        @Override
        public BackOffContext start(RetryContext context) {
            return new RetryContextBackOffContext(context);
        }

        @Override
        public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
            RetryContext retryContext = ((RetryContextBackOffContext) backOffContext).retryContext();
            Throwable throwable = retryContext.getLastThrowable();
            long backoffMillis = is502Or503Error(throwable) ? http502503BackoffMillis
                    : defaultBackoffMillis;

            try {
                Thread.sleep(backoffMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BackOffInterruptedException("Retry backoff interrupted", e);
            }
        }

        private boolean is502Or503Error(Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                String message = current.getMessage();
                if (message != null && (message.contains("HTTP 502") || message.contains("HTTP 503"))) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }
    }

    private record RetryContextBackOffContext(RetryContext retryContext) implements BackOffContext {
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
