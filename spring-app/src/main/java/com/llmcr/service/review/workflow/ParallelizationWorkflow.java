package com.llmcr.service.review.workflow;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.SummaryAgent.ItemAnswer;
import com.llmcr.util.GitDiffParser.CodeChange;

/**
 * Iterates over every checklist item and delegates each one to
 * {@link ChecklistItemOrchestrator}, collecting the results.
 *
 * <p>
 * Items are processed sequentially (no true parallelism is required).
 */
@Component
public class ParallelizationWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ParallelizationWorkflow.class);

    private final ChecklistItemOrchestrator orchestrator;

    public ParallelizationWorkflow(ChecklistItemOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Process all {@code checklistItems} and return one {@link ItemAnswer} per
     * item.
     */
    public List<ItemAnswer> run(List<CodeChange> codeChanges, List<String> checklistItems) {
        List<ItemAnswer> answers = new ArrayList<>();
        List<String> safeItems = checklistItems == null ? List.of() : checklistItems;

        for (String item : safeItems) {
            log.info("processing item: {}", item);
            ItemAnswer answer = orchestrator.run(codeChanges, item);
            answers.add(answer);
            log.info("answer: {}", item, answer);
        }

        return answers;
    }
}
