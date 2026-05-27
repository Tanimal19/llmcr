package com.llmcr;

import com.llmcr.config.SystemConfig;
import com.llmcr.config.SystemConfigReader;
import com.llmcr.domain.sse.SseTaskManager;
import com.llmcr.feature.chat.ChatService;
import com.llmcr.feature.chat.ChatService.ChatResponse;
import com.llmcr.feature.review.CodeReviewService;
import com.llmcr.feature.review.CodeReviewService.CodeReviewInput;
import com.llmcr.feature.sync.ConfigSyncService;
import com.llmcr.feature.sync.SourceSyncTask;
import com.llmcr.feature.sync.etl.LoadService;
import com.llmcr.feature.sync.source.SourceSyncService;
import com.llmcr.feature.sync.source.TrackRootPreview;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@CrossOrigin(origins = "${server.cors.allowed-origins:*}")
@RequestMapping("/api")
public class APIController {

    private static final Logger logger = LoggerFactory.getLogger(APIController.class);

    private final SseTaskManager sseTaskManager;
    private final SystemConfig applicationProperties;
    private final SystemConfigReader configReader;
    private final ChatService chatService;
    private final CodeReviewService codeReviewService;
    private final SourceSyncService sourceSyncService;
    private final SourceSyncTask sourceSyncTask;

    public APIController(
            SseTaskManager sseTaskManager,
            SystemConfig applicationProperties,
            SystemConfigReader configReader,
            ChatService chatService,
            CodeReviewService codeReviewService,
            ConfigSyncService configSyncService,
            SourceSyncService sourceSyncService,
            SourceSyncTask sourceSyncTask,
            LoadService loadService) {
        this.sseTaskManager = sseTaskManager;
        this.applicationProperties = applicationProperties;
        this.configReader = configReader;

        this.chatService = chatService;
        this.codeReviewService = codeReviewService;
        this.sourceSyncService = sourceSyncService;
        this.sourceSyncTask = sourceSyncTask;

        // On application startup, we want to ensure that the track roots and
        // collections are in sync with the configuration.
        boolean changed = false;
        changed = configSyncService.syncTrackRoots();
        changed = configSyncService.syncConfiguredCollections();
        if (changed) {
            // If there is any change in track roots or collections, we need to rebuild all
            // chunks to update the collection-chunk mapping in the vector store.
            loadService.rebuildCollectionChunkMapping();
        }
        // We always reload all collections on startup to ensure the vector store is up
        // to date with the latest chunks.
        loadService.reloadAllCollections();
    }

    public record ChatRequest(String query) {
    }

    public record SetRagRequest(Set<String> trackRootPaths) {
    }

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

        return Map.of(
                "configPath", configPath, "config", applicationProperties, "lastSyncTime", lastSyncTime);
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }

        String query = request.query();
        logger.info("Chat request received: {}", query);

        return chatService.chat(query);
    }

    @PostMapping("/getrag")
    public Map<String, Boolean> getRagScope() {
        return chatService.getRagScope();
    }

    @PostMapping("/setrag")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRagScope(@RequestBody SetRagRequest request) {
        if (request == null || request.trackRootPaths() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trackRootPaths must not be null");
        }

        logger.info("Set RAG scope request received: {}", request.trackRootPaths());
        chatService.setRagScope(request.trackRootPaths());
    }

    @PostMapping(value = "/review", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter review(@RequestBody CodeReviewInput request) {
        logger.info("Code review request received: {}", request);
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
        return sseTaskManager.start(sourceSyncTask, null);
    }

    @PostMapping("/cancel/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSseTask(@PathVariable String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId must not be blank");
        }

        sseTaskManager.requestCancellation(taskId, "client_request");
    }
}
