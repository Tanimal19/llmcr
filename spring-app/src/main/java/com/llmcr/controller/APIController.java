package com.llmcr.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.llmcr.service.ChatService;
import com.llmcr.service.ChatService.ChatResponse;
import com.llmcr.service.etl.ETLService;
import com.llmcr.service.etl.LoadService;
import com.llmcr.service.review.CodeReviewService;
import com.llmcr.service.review.CodeReviewService.CodeReviewOutput;
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
            loadService.reloadAllChunks();
        }
    }

    public record ChatRequest(String query) {
    }

    public record ReviewRequest(String pullRequestJsonPath) {
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

    @PostMapping("/review")
    public CodeReviewOutput review(@RequestBody ReviewRequest request) {
        String pullRequestJsonPath = request == null ? null : request.pullRequestJsonPath();
        requireNonBlank(pullRequestJsonPath, "pullRequestJsonPath must not be blank");
        logger.info("[APIService] Code review request received: {}", pullRequestJsonPath);
        return codeReviewService.review(pullRequestJsonPath, false);
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
}
