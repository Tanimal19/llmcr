package com.llmcr.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * This class manages the lifecycle of SSE (Server Sent Envents) tasks.
 */
@Component
public class SseTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(APIController.class);

    private final ConcurrentMap<String, TaskContext> currentTasks = new ConcurrentHashMap<>();

    public record TaskStartEvent(String name, String id) {
    }

    public record TaskProgressEvent(Boolean isError, String stage, String message) {
    }

    private static final class TaskContext {

        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        private volatile CompletableFuture<Void> future;

        private boolean isCancellationRequested() {
            return cancellationRequested.get();
        }

        private void setFuture(CompletableFuture<Void> future) {
            this.future = future;
            if (cancellationRequested.get()) {
                future.cancel(true);
            }
        }

        private void requestCancellation() {
            cancellationRequested.set(true);
            CompletableFuture<Void> currentFuture = future;
            if (currentFuture != null) {
                currentFuture.cancel(true);
            }
        }
    }

    /**
     * Entrypoint for starting the SSE task. This method will be called by the
     * controller when a new task is initiated. It sets up the SseEmitter, manages
     * the task context, and handles the asynchronous execution of the task logic
     * defined in the execute() method.
     */
    public <I, O> SseEmitter start(SseTaskObject<I, O> taskObject, I request) {
        SseEmitter emitter = new SseEmitter(0L);
        String taskId = UUID.randomUUID().toString();
        TaskContext taskContext = new TaskContext();
        currentTasks.put(taskId, taskContext);

        emitter.onTimeout(() -> {
            logger.warn("SSE task timeout: taskId={}", taskId);
            requestCancellation(taskId, "timeout");
        });
        emitter.onCompletion(() -> logger.info("SSE task completed: taskId={}", taskId));

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                sendSseEvent(emitter, "start", new TaskStartEvent(taskObject.getTaskName(), taskId));

                Object output = taskObject.execute(
                        request,
                        progress -> sendSseEvent(emitter, "progress", progress),
                        taskContext::isCancellationRequested);

                sendSseEvent(emitter, "result", output);
                emitter.complete();
            } catch (Exception ex) {
                logger.error("SSE task failed", ex);
                sendSseEvent(emitter, "error", ex);
                emitter.complete();
            } finally {
                currentTasks.remove(taskId);
            }
        });
        taskContext.setFuture(future);

        return emitter;
    }

    /**
     * Subclasses use this to send SSE events in a consistent way.
     */
    public static void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to emit SSE event: " + eventName, ex);
        }
    }

    public void requestCancellation(String taskId, String reason) {
        TaskContext taskContext = currentTasks.get(taskId);
        if (taskContext == null) {
            logger.info("cancel ignored (task not found): taskId={} reason={}", taskId, reason);
            return;
        }

        taskContext.requestCancellation();
        logger.info("cancellation requested: taskId={} reason={}", taskId, reason);
    }
}
