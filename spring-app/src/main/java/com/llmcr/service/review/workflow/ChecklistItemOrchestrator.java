package com.llmcr.service.review.workflow;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.ComputationAgent;
import com.llmcr.service.review.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.service.review.agent.ComputationAgent.ComputationAgentOutput;
import com.llmcr.service.review.agent.SummaryAgent.ItemAnswer;
import com.llmcr.util.GitDiffParser.CodeChange;

/**
 * Orchestrator for a single checklist item.
 *
 * <p>
 * Repeatedly invokes the {@link ComputationAgent}. If the agent signals that
 * additional data is required it delegates to {@link RetrievalLoop} to satisfy
 * the query, then feeds the result back into the next computation iteration.
 */
@Component
public class ChecklistItemOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChecklistItemOrchestrator.class);
    private static final int MAX_ITERATIONS = 3;

    private final ComputationAgent computationAgent;
    private final RetrievalLoop retrievalLoop;

    public ChecklistItemOrchestrator(ComputationAgent computationAgent, RetrievalLoop retrievalLoop) {
        this.computationAgent = computationAgent;
        this.retrievalLoop = retrievalLoop;
    }

    /**
     * Orchestrate computation and (optional) retrieval for one
     * {@code checklistItem}.
     *
     * @return an {@link ItemAnswer} with the final answer for the item.
     */
    public ItemAnswer run(List<CodeChange> codeChanges, String checklistItem) {
        String previousAnalysis = null;
        String retrievalResult = null;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.info("item={} iteration={}", checklistItem, i);

            ComputationAgentOutput output = computationAgent.execute(
                    new ComputationAgentInput(codeChanges, checklistItem, previousAnalysis, retrievalResult));

            if (!output.needsAdditionalData()) {
                return new ItemAnswer(checklistItem, output.answer());
            }

            log.info("item={} needs retrieval query={}", checklistItem, output.dataQuery());
            retrievalResult = retrievalLoop.run(output.dataQuery());
            previousAnalysis = output.answer();
        }

        // Max iterations reached – return best effort answer from last computation
        ComputationAgentOutput finalOutput = computationAgent.execute(
                new ComputationAgentInput(codeChanges, checklistItem, previousAnalysis, retrievalResult));
        return new ItemAnswer(checklistItem, finalOutput.answer());
    }
}
