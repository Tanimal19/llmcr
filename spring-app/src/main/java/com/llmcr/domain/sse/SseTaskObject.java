package com.llmcr.domain.sse;

import com.llmcr.domain.exception.APIServiceException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SseTaskObject<I, O> {
  private static final Logger logger = LoggerFactory.getLogger(SseTaskObject.class);
  private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
  private final AtomicReference<CompletableFuture<Void>> future = new AtomicReference<>();

  public record SseTaskProgress(Boolean isError, String stage, String message) {}

  public abstract String getTaskName();

  public abstract O execute(
      I input, Consumer<SseTaskProgress> progressListener, BooleanSupplier cancellationRequested);

  public O execute(I input) {
    return execute(input, null, () -> false);
  }

  protected boolean isCancellationRequested() {
    return cancellationRequested.get();
  }

  protected void setFuture(CompletableFuture<Void> future) {
    this.future.set(future);
    if (cancellationRequested.get()) {
      future.cancel(true);
    }
  }

  protected void requestCancellation() {
    cancellationRequested.set(true);
    CompletableFuture<Void> currentFuture = future.get();
    if (currentFuture != null) {
      currentFuture.cancel(true);
    }
  }

  public static void throwIfCancelled(BooleanSupplier cancellationRequested) {
    if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
      throw new APIServiceException(APIServiceException.ErrorCode.SSE_TASK_CANCELLED);
    }
  }

  public static void emitProgress(
      Consumer<SseTaskProgress> progressListener, String stage, String message) {
    if (progressListener == null) {
      return;
    }
    logger.info("[{}] {}", stage, message);
    progressListener.accept(new SseTaskProgress(false, stage, message));
  }
}
