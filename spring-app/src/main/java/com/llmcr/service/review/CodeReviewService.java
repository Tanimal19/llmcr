package com.llmcr.service.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.llmcr.service.review.workflow.ChainWorkflow;

/**
 * Multi-Agent Code Review — entry point
 *
 * <p>
 * Delegates the full review pipeline to {@link ChainWorkflow}, which
 * implements the chain: InterpretationAgent → PlanningAgent →
 * ParallelizationWorkflow → SummaryAgent.
 */
@Service
public class CodeReviewService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);

    public record ReviewRequest(String codeChanges, String codeAnalysis) {
        public ReviewRequest(String codeChanges) {
            this(codeChanges, "");
        }
    }

    private final ChainWorkflow chainWorkflow;

    public CodeReviewService(ChainWorkflow chainWorkflow) {
        this.chainWorkflow = chainWorkflow;
    }

    public String review(ReviewRequest request) {
        log.info("Starting code review pipeline.");
        String report = chainWorkflow.run(request.codeChanges(), request.codeAnalysis());
        log.info("Review pipeline completed.");
        return report;
    }
}
