package com.llmcr.service.review.workflow;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ParallelizationWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ParallelizationWorkflow.class);

    private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ItemOrchestrator itemOrchestrator;

    public ParallelizationWorkflow(ItemOrchestrator itemOrchestrator) {
        this.itemOrchestrator = itemOrchestrator;
    }

    public List<String> run(String codeChanges, List<String> checklistItems) {
        List<CompletableFuture<String>> futures = checklistItems.stream()
                .map(item -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return itemOrchestrator.processItem(codeChanges, item);
                            } catch (Exception e) {
                                log.error("[ParallelizationWorkflow] Error processing item '{}': {}",
                                        item, e.getMessage(), e);
                                return "(error: " + e.getMessage() + ")";
                            }
                        },
                        VIRTUAL_THREAD_EXECUTOR))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }
}
