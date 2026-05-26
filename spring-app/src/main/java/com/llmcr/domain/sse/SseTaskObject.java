package com.llmcr.domain.sse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.llmcr.domain.exception.APIServiceException;

public abstract class SseTaskObject<I, O> {
    private static final Logger logger = LoggerFactory.getLogger(SseTaskObject.class);
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private volatile CompletableFuture<Void> future;

    public record SseTaskProgress(Boolean isError, String stage, String message) {
    }

    public abstract String getTaskName();

    public abstract O execute(I input, Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested);

    public O execute(I input) {
        return execute(input, null, () -> false);
    }

    protected boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    protected void setFuture(CompletableFuture<Void> future) {
        this.future = future;
        if (cancellationRequested.get()) {
            future.cancel(true);
        }
    }

    protected void requestCancellation() {
        cancellationRequested.set(true);
        CompletableFuture<Void> currentFuture = future;
        if (currentFuture != null) {
            currentFuture.cancel(true);
        }
    }

    protected static void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.SSE_TASK_CANCELLED);
        }
    }

    protected static void emitProgress(Consumer<SseTaskProgress> progressListener, String stage, String message) {
        if (progressListener == null) {
            return;
        }
        logger.info("[{}] {}", stage, message);
        progressListener.accept(new SseTaskProgress(false, stage, message));
    }
}
