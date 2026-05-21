package com.llmcr.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.llmcr.service.etl.ETLService;
import com.llmcr.service.review.CodeReviewService;
import com.llmcr.service.review.CodeReviewService.CodeReviewOutput;
import com.llmcr.service.sync.SourceSyncService;
import com.llmcr.service.sync.SourceSyncService.TrackRootPreview;

/**
 * This is a mock API service that simulates interactions with frontend and
 * backend system. This is mainly used for testing and development purposes.
 * In production, frontend should directly call each service components.
 */
@Service
public class MockAPIService {

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

    public String chat(String query) {
        return chatService.chat(query);
    }

    public CodeReviewOutput review(String pullRequestJsonPath) {
        return codeReviewService.review(pullRequestJsonPath, false);
    }

    public List<TrackRootPreview> lsdb() {
        return sourceSyncService.getAllTrackRootPreview();
    }

    public void sync() {
        sourceSyncService.syncAllTrackRoot();
        if (ENABLE_ETL) {
            etlService.run();
        }
    }

    public void sync(Long trackRootId) {
        sourceSyncService.syncTrackRoot(trackRootId);
        if (ENABLE_ETL) {
            etlService.run();
        }
    }

}
