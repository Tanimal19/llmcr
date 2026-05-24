package com.llmcr.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.llmcr.api.APIServiceException.ErrorCode;
import com.llmcr.service.ChatService;
import com.llmcr.service.ChatService.ChatResponse;
import com.llmcr.service.etl.ETLService;
import com.llmcr.service.etl.LoadService;
import com.llmcr.service.review.CodeReviewService;
import com.llmcr.service.review.CodeReviewService.CodeReviewOutput;
import com.llmcr.service.review.CodeReviewService.ReviewStageProgress;
import com.llmcr.service.sync.ConfigSyncService;
import com.llmcr.service.sync.SourceSyncService;
import com.llmcr.service.sync.SourceSyncService.TrackRootPreview;

@RestController
@CrossOrigin(origins = "${server.cors.allowed-origins:*}")
@RequestMapping("/api")
public class APIController {

    private static final Logger logger = LoggerFactory.getLogger(APIController.class);

    private final ChatService chatService;
    private final CodeReviewService codeReviewService;
    private final SourceSyncService sourceSyncService;
    private final ETLService etlService;
    private final ConcurrentMap<String, ReviewTaskContext> reviewTasks = new ConcurrentHashMap<>();

    private static final boolean ENABLE_ETL = false;

    public APIController(
            ChatService chatService,
            CodeReviewService codeReviewService,
            ConfigSyncService configSyncService,
            SourceSyncService sourceSyncService,
            ETLService etlService,
            LoadService loadService) {
        this.chatService = chatService;
        this.codeReviewService = codeReviewService;
        this.sourceSyncService = sourceSyncService;
        this.etlService = etlService;

        boolean changed = false;
        changed = configSyncService.syncTrackRoots();
        changed = configSyncService.syncConfiguredCollections();
        if (changed) {
            // If there is any change in track roots or collections, we need to reload all
            // chunks to update the collection-chunk mapping in the vector store.
            loadService.rebuildCollectionChunkMapping();
            loadService.reloadAllCollections();
        }
    }

    public record ChatRequest(String query) {
    }

    public record ReviewRequest(String pullRequestJsonPath, boolean useMock) {
    }

    public record ReviewErrorEvent(String code, String message) {
    }

    public record ReviewTaskEvent(String taskId) {
    }

    public record RagScopeRequest(Set<String> trackRootPaths) {
    }

    @GetMapping("/health")
    public String health() {
        logger.info("[APIService] Health check endpoint called");
        return "ok";
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String query = request == null ? null : request.query();
        requireNonBlank(query, "query must not be blank");
        logger.info("[APIService] Chat request received: {}", query);
        return chatService.chat(query);
    }

    @GetMapping("/rag-scope")
    public Map<String, Boolean> getRagScope() {
        logger.info("[APIService] Get RAG scope request received");
        return chatService.getRagScope();
    }

    @PostMapping("/rag-scope")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRagScope(@RequestBody RagScopeRequest request) {
        Set<String> trackRootPaths = request == null ? null : request.trackRootPaths();
        requireNonEmpty(trackRootPaths, "trackRootPaths must not be empty");
        logger.info("[APIService] Set RAG scope request received: {}", trackRootPaths);
        chatService.setRagScope(trackRootPaths);
    }

    @PostMapping(value = "/review", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter review(@RequestBody ReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        logger.info("[APIService] Code review request received: {}", request.pullRequestJsonPath());

        SseEmitter emitter = new SseEmitter(0L);
        String taskId = UUID.randomUUID().toString();
        ReviewTaskContext taskContext = new ReviewTaskContext();
        reviewTasks.put(taskId, taskContext);

        emitter.onTimeout(() -> {
            logger.warn("[APIService] review SSE timeout: taskId={} path={}", taskId, request.pullRequestJsonPath());
            requestCancellation(taskId, "timeout");
        });
        emitter.onCompletion(() -> logger.info("[APIService] review SSE completed: taskId={} path={}", taskId,
                request.pullRequestJsonPath()));

        sendSseEvent(emitter, "task", new ReviewTaskEvent(taskId));

        CompletableFuture<Void> reviewFuture = CompletableFuture.runAsync(() -> {
            try {
                sendSseEvent(emitter, "progress", new ReviewStageProgress(
                        "PIPELINE", "STARTED", "Review request accepted"));

                CodeReviewOutput output = codeReviewService.review(
                        request.pullRequestJsonPath(),
                        request.useMock(),
                        progress -> sendSseEvent(emitter, "progress", progress),
                        taskContext::isCancellationRequested);

                sendSseEvent(emitter, "result", output);
                emitter.complete();
            } catch (Exception ex) {
                logger.error("[APIService] review SSE failed: {}", request.pullRequestJsonPath(), ex);
                if (ex instanceof APIServiceException apiEx) {
                    sendSseEvent(emitter, "error", new ReviewErrorEvent(
                            apiEx.getErrorCode().name(),
                            apiEx.getMessage()));
                } else {
                    sendSseEvent(emitter, "error", new ReviewErrorEvent(
                            ErrorCode.REVIEW_PIPELINE_FAILED.name(),
                            ex.getMessage()));
                }
                emitter.complete();
            } finally {
                reviewTasks.remove(taskId);
            }
        });
        taskContext.setFuture(reviewFuture);

        return emitter;
    }

    @PostMapping("/review/{taskId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelReview(@PathVariable String taskId) {
        requireNonBlank(taskId, "taskId must not be blank");
        requestCancellation(taskId, "client_request");
    }

    @GetMapping("/lsdb")
    public List<TrackRootPreview> lsdb() {
        logger.info("[APIService] List track roots request received");
        return sourceSyncService.getAllTrackRootPreview();
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sync() {
        logger.info("[APIService] Sync all request received");
        sourceSyncService.syncAllTrackRootSource();
        if (ENABLE_ETL) {
            etlService.run();
        }
    }

    @PostMapping("/sync/{trackRootId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void syncByTrackRootId(@PathVariable Long trackRootId) {
        logger.info("[APIService] Sync request received for track root id: {}", trackRootId);
        sourceSyncService.syncTrackRootSource(trackRootId);
        if (ENABLE_ETL) {
            etlService.run();
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonEmpty(Set<String> values, String message) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to emit SSE event: " + eventName, ex);
        }
    }

    private void requestCancellation(String taskId, String reason) {
        ReviewTaskContext taskContext = reviewTasks.get(taskId);
        if (taskContext == null) {
            logger.info("[APIService] cancel ignored (task not found): taskId={} reason={}", taskId, reason);
            return;
        }

        taskContext.requestCancellation();
        logger.info("[APIService] cancellation requested: taskId={} reason={}", taskId, reason);
    }

    private static final class ReviewTaskContext {
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
}
