package com.llmcr.service.review.workflow;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ParallelizationWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ParallelizationWorkflow.class);

    private final ItemOrchestrator itemOrchestrator;

    public ParallelizationWorkflow(ItemOrchestrator itemOrchestrator) {
        this.itemOrchestrator = itemOrchestrator;
    }

    public List<String> run(String codeChanges, List<String> checklistItems) {
        List<String> results = new ArrayList<>();

        for (String item : checklistItems) {
            try {
                results.add(itemOrchestrator.processItem(codeChanges, item));
            } catch (Exception e) {
                log.error("Error processing item '{}': {}",
                        item, e.getMessage(), e);
                results.add("(error: " + e.getMessage() + ")");
            }
        }

        return results;
    }
}
