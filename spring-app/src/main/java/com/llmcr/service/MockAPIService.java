package com.llmcr.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.llmcr.service.etl.ETLService;
import com.llmcr.service.review.CodeReviewService;
import com.llmcr.service.review.CodeReviewService.CodeReviewOutput;
import com.llmcr.service.sync.SourceSyncService;
import com.llmcr.service.sync.SourceSyncService.TrackRootPreview;

/**
 * This is a mock API service that simulates interactions with frontend and
 * backend system.
 */
@Service
public class MockAPIService {

    /**
     * Represents loading/progress state for long-running operations.
     *
     * @param stage    logical stage name
     * @param message  human-readable progress message
     * @param progress progress percentage in range 0-100; can be null if unknown
     */
    public record LoadingState(String stage, String message, Integer progress) {
    }

    private final ChatService chatService;
    private final CodeReviewService codeReviewService;
    private final SourceSyncService sourceSyncService;
    private final ETLService etlService;

    private static final boolean ENABLE_ETL = false;

    public MockAPIService(
            ChatService chatService,
            CodeReviewService codeReviewService,
            SourceSyncService sourceSyncService,
            ETLService etlService) {
        this.chatService = chatService;
        this.codeReviewService = codeReviewService;
        this.sourceSyncService = sourceSyncService;
        this.etlService = etlService;
    }

    /**
     * Sends a user query to the chat pipeline.
     *
     * @param query user input question or prompt
     * @return assistant response text
     */
    public String chat(String query) {
        return chatService.chat(query);
    }

    /**
     * Returns currently enabled RAG scope root paths for the chat service.
     *
     * @return set of tracked root paths used by retrieval
     */
    public Set<String> getRagScope() {
        return chatService.getRagScope();
    }

    /**
     * Replaces current RAG scope with the provided root paths.
     *
     * @param trackRootPaths root paths to enable for retrieval
     */
    public void setRagScope(Set<String> trackRootPaths) {
        chatService.setRagScope(trackRootPaths);
    }

    /**
     * Runs code review for a pull request JSON input.
     *
     * @param pullRequestJsonPath file path to PR JSON payload
     * @return structured code review output
     */
    public CodeReviewOutput review(String pullRequestJsonPath) {
        return codeReviewService.review(pullRequestJsonPath, false);
    }

    /**
     * Lists all tracked source roots currently stored in the database.
     *
     * @return preview list of tracked roots
     */
    public List<TrackRootPreview> lsdb() {
        return sourceSyncService.getAllTrackRootPreview();
    }

    /**
     * Synchronizes all tracked source roots.
     */
    public void sync() {
        sourceSyncService.syncAllTrackRoot();
        if (ENABLE_ETL) {
            etlService.run();
        }
    }

    /**
     * Synchronizes a specific tracked source root by its identifier.
     *
     * @param trackRootId identifier of the tracked root
     */
    public void sync(Long trackRootId) {
        sourceSyncService.syncTrackRoot(trackRootId);
        if (ENABLE_ETL) {
            etlService.run();
        }
    }
}
