package com.llmcr.service;

import java.util.List;

import com.llmcr.agent.QuestionAnswerAgent;
import com.llmcr.service.SyncService.TrackRootPreview;
import com.llmcr.service.etl.ETLService;
import com.llmcr.service.review.CodeReviewService;
import com.llmcr.service.review.CodeReviewService.CodeReviewOutput;

/**
 * This is a mock API service that simulates interactions with frontend and
 * backend system. This is mainly used for testing and development purposes.
 * In production, frontend should directly call each service components.
 */
public class MockAPIService {

    public record LoadingState(String stage, String message, Integer progress) {
    }

    private final QuestionAnswerAgent questionAnswerAgent;
    private final CodeReviewService codeReviewService;
    private final SyncService syncService;
    private final ETLService etlService;

    private static final boolean ENABLE_ETL = false;

    public MockAPIService(
            QuestionAnswerAgent questionAnswerAgent,
            CodeReviewService codeReviewService,
            SyncService syncService,
            ETLService etlService) {
        this.questionAnswerAgent = questionAnswerAgent;
        this.codeReviewService = codeReviewService;
        this.syncService = syncService;
        this.etlService = etlService;
    }

    public String chat(String message) {
        return questionAnswerAgent.execute(message);
    }

    // TODO: setrag()

    public CodeReviewOutput review(String pullRequestJsonPath) {
        return codeReviewService.review(pullRequestJsonPath, false);
    }

    public List<TrackRootPreview> lsdb() {
        return syncService.getAllTrackRootPreview();
    }

    public void sync() {
        syncService.syncAllTrackRoot();
        if (ENABLE_ETL) {
            etlService.run();
        }
    }

    public void sync(Long trackRootId) {
        syncService.syncTrackRoot(trackRootId);
        if (ENABLE_ETL) {
            etlService.run();
        }
    }

}
