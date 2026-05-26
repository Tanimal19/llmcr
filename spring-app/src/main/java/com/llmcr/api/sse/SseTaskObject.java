package com.llmcr.api.sse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.llmcr.api.APIServiceException;

public abstract class SseTaskObject<I, O> {
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private volatile CompletableFuture<Void> future;

    public record SseTaskProgress(Boolean isError, String stage, String message) {
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

    public abstract String getTaskName();

    public O execute(I input) {
        return execute(input, null, () -> false);
    }

    public abstract O execute(I input, Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested);

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
        progressListener.accept(new SseTaskProgress(false, stage, message));
    }
}
