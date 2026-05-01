package com.llmcr.service.review;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.llmcr.service.review.util.GitDiffParser;
import com.llmcr.service.review.util.GitDiffParser.FileChange;
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

    public record ReviewRequest(String diffFilePath, String codeAnalysis) {
        public ReviewRequest(String diffFilePath) {
            this(diffFilePath, "");
        }
    }

    private final ChainWorkflow chainWorkflow;

    public CodeReviewService(ChainWorkflow chainWorkflow) {
        this.chainWorkflow = chainWorkflow;
    }

    public String review(ReviewRequest request) {
        log.info("Starting code review pipeline.");
        List<FileChange> fileChanges = GitDiffParser.parseDiffFile(request.diffFilePath());
        String codeChanges = toCodeChangesInput(fileChanges);
        String report = chainWorkflow.run(codeChanges, request.codeAnalysis());
        log.info("Review pipeline completed.");
        return report;
    }

    private String toCodeChangesInput(List<FileChange> fileChanges) {
        return fileChanges.stream()
                .map(fileChange -> "File: " + fileChange.filePath() + "\n" + fileChange.diffContent())
                .collect(Collectors.joining("\n\n"));
    }
}
