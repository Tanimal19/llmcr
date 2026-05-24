package com.llmcr.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
        emitter.onTimeout(() -> logger.warn("[APIService] review SSE timeout: {}", request.pullRequestJsonPath()));
        emitter.onCompletion(() -> logger.info("[APIService] review SSE completed: {}", request.pullRequestJsonPath()));

        CompletableFuture.runAsync(() -> {
            try {
                sendSseEvent(emitter, "progress", new ReviewStageProgress(
                        "PIPELINE", "STARTED", 0, 6, "Review request accepted"));

                CodeReviewOutput output = codeReviewService.review(
                        request.pullRequestJsonPath(),
                        request.useMock(),
                        progress -> sendSseEvent(emitter, "progress", progress));

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
            }
        });

        return emitter;
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
}
