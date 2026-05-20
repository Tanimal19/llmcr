package com.llmcr.service;

import java.util.List;

import com.llmcr.agent.QuestionAnswerAgent;
import com.llmcr.service.SyncService.TrackRootPreview;
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

    public MockAPIService(
            QuestionAnswerAgent questionAnswerAgent,
            CodeReviewService codeReviewService,
            SyncService syncService) {
        this.questionAnswerAgent = questionAnswerAgent;
        this.codeReviewService = codeReviewService;
        this.syncService = syncService;
    }

    public String chat(String message) {
        return questionAnswerAgent.execute(message);
    }

    public void setChatRetrievalScope(String collectionName) {
        // TODO: implement this method to allow user to configure chatbot
    }

    public CodeReviewOutput review(String pullRequestJsonPath) {
        return codeReviewService.review(pullRequestJsonPath, false);
    }

    public List<TrackRootPreview> lsdb() {
        return syncService.getAllTrackRootPreview();
    }

    public void sync() {
        syncService.syncAllTrackRoot();
    }

    public void sync(Long trackRootId) {
        syncService.syncTrackRoot(trackRootId);
    }

}
