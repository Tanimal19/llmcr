package com.llmcr.api;

import com.llmcr.config.ApplicationProperties;
import com.llmcr.config.ConfigReader;
import com.llmcr.service.ChatService;
import com.llmcr.service.ChatService.ChatResponse;
import com.llmcr.service.etl.LoadService;
import com.llmcr.service.review.CodeReviewService;
import com.llmcr.service.review.CodeReviewService.CodeReviewInput;
import com.llmcr.service.sync.ConfigSyncService;
import com.llmcr.service.sync.SourceSyncService;
import com.llmcr.service.sync.SourceSyncService.TrackRootPreview;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

@RestController
@CrossOrigin(origins = "${server.cors.allowed-origins:*}")
@RequestMapping("/api")
public class APIController {

    private static final Logger logger = LoggerFactory.getLogger(APIController.class);

    private final SseTaskManager sseTaskManager;
    private final ApplicationProperties applicationProperties;
    private final ConfigReader configReader;
    private final ChatService chatService;
    private final CodeReviewService codeReviewService;
    private final SourceSyncService sourceSyncService;

    public APIController(
        SseTaskManager sseTaskManager,
        ApplicationProperties applicationProperties,
        ConfigReader configReader,
        ChatService chatService,
        CodeReviewService codeReviewService,
        ConfigSyncService configSyncService,
        SourceSyncService sourceSyncService,
        LoadService loadService
    ) {
        this.sseTaskManager = sseTaskManager;
        this.applicationProperties = applicationProperties;
        this.configReader = configReader;

        this.chatService = chatService;
        this.codeReviewService = codeReviewService;
        this.sourceSyncService = sourceSyncService;

        // On application startup, we want to ensure that the track roots and
        // collections are in sync with the configuration.
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

    public record ChatRequest(String query) {}

    public record SetRagRequest(Set<String> trackRootPaths) {}

    @GetMapping("/health")
    public String health() {
        logger.info("Health check endpoint called");
        return "ok";
    }

    @GetMapping("/info")
    public Map<String, Object> getInfo() {
        logger.info("Get info request received");
        String configPath = configReader.getConfigFilePath();
        String lastSyncTime = sourceSyncService.getLastAllSyncTime();

        return Map.of("configPath", configPath, "config", applicationProperties, "lastSyncTime", lastSyncTime);
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String query = request == null ? null : request.query();
        logger.info("Chat request received: {}", query);
        requireNonBlank(query, "query must not be blank");

        return chatService.chat(query);
    }

    @PostMapping("/getrag")
    public Map<String, Boolean> getRagScope() {
        return chatService.getRagScope();
    }

    @PostMapping("/setrag")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRagScope(@RequestBody SetRagRequest request) {
        logger.info("Set RAG scope request received: {}", request.trackRootPaths());
        requireNonEmpty(request.trackRootPaths(), "trackRootPaths must not be empty");

        chatService.setRagScope(request.trackRootPaths());
    }

    @PostMapping(value = "/review", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter review(@RequestBody CodeReviewInput request) {
        logger.info(
            "Code review request received for jsonFilePath={}, useMockData={}",
            request.jsonFilePath(),
            request.useMockData()
        );
        return sseTaskManager.start(codeReviewService, request);
    }

    @GetMapping("/lsdb")
    public List<TrackRootPreview> lsdb() {
        logger.info("List track roots request received");
        return sourceSyncService.getAllTrackRootPreview();
    }

    @PostMapping(value = "/sync", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sync() {
        logger.info("Sync request received");
        return sseTaskManager.start(sourceSyncService, null);
    }

    @PostMapping("/cancel/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSseTask(@PathVariable String taskId) {
        requireNonBlank(taskId, "taskId must not be blank");
        sseTaskManager.requestCancellation(taskId, "client_request");
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
}
