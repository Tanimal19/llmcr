package com.llmcr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {

    private static final long DEFAULT_BACKOFF_MILLIS = 10_000L;
    private static final long HTTP_502_503_BACKOFF_MILLIS = 30_000L;

    @Bean
    public RetryTemplate defaultRetryTemplate() {
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

}
