package com.llmcr.config;

import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;

public final class LocalModelBackOffPolicy implements BackOffPolicy {

    private static final long DEFAULT_BACKOFF_MILLIS = 10_000L;
    private static final long HTTP502503_BACKOFF_MILLIS = 30_000L;

    public LocalModelBackOffPolicy() {
    }

    @Override
    public BackOffContext start(RetryContext context) {
        return new RetryContextBackOffContext(context);
    }

    @Override
    public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
        RetryContext retryContext = ((RetryContextBackOffContext) backOffContext).retryContext();
        Throwable throwable = retryContext.getLastThrowable();
        long backoffMillis = is502Or503Error(throwable) ? HTTP502503_BACKOFF_MILLIS
                : DEFAULT_BACKOFF_MILLIS;

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

    private record RetryContextBackOffContext(RetryContext retryContext) implements BackOffContext {
    }
}
