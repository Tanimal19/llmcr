package com.llmcr.service.review.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.ComputationAgent;
import com.llmcr.service.review.agent.ComputationAgent.ComputationDecision;
import com.llmcr.service.review.agent.ComputationAgent.ComputationInput;

@Component
public class ItemOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ItemOrchestrator.class);

    private static final int MAX_RETRIEVAL_ROUNDS = 3;

    private final ComputationAgent computationAgent;
    private final RetrievalOrchestrator retrievalOrchestrator;

    public ItemOrchestrator(ComputationAgent computationAgent, RetrievalOrchestrator retrievalOrchestrator) {
        this.computationAgent = computationAgent;
        this.retrievalOrchestrator = retrievalOrchestrator;
    }

    public String processItem(String codeChanges, String checklistItem) {
        String accumulatedContext = "";

        for (int round = 0; round <= MAX_RETRIEVAL_ROUNDS; round++) {
            ComputationDecision decision = computationAgent.execute(
                    new ComputationInput(codeChanges, checklistItem, accumulatedContext));

            if (decision == null) {
                log.warn("Null decision for item '{}'", checklistItem);
                return "(no answer)";
            }

            if (!decision.needsMoreData()) {
                return decision.answer();
            }

            if (round == MAX_RETRIEVAL_ROUNDS) {
                log.warn("Max retrieval rounds reached for item '{}'", checklistItem);
                return decision.answer().isBlank() ? "(max retrieval rounds reached)" : decision.answer();
            }

            log.debug("Round {}: querying RetrievalOrchestrator for '{}'",
                    round + 1, decision.dataQuery());
            String retrieved = retrievalOrchestrator.retrieve(decision.dataQuery());
            accumulatedContext = mergeContext(accumulatedContext, decision.dataQuery(), retrieved);
        }

        return "(no answer)";
    }

    private String mergeContext(String existing, String query, String retrieved) {
        String block = "### Query: " + query + "\n" + retrieved;
        return existing.isBlank() ? block : existing + "\n\n" + block;
    }
}
