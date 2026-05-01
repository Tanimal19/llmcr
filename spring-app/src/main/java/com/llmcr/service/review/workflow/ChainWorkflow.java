package com.llmcr.service.review.workflow;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.InterpretationAgent;
import com.llmcr.service.review.agent.PlanningAgent;
import com.llmcr.service.review.agent.SummaryAgent;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationInput;
import com.llmcr.service.review.agent.PlanningAgent.PlanningInput;
import com.llmcr.service.review.agent.PlanningAgent.PlanningOutput;
import com.llmcr.service.review.agent.SummaryAgent.SummaryInput;

@Component
public class ChainWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ChainWorkflow.class);

    private final InterpretationAgent interpretationAgent;
    private final PlanningAgent planningAgent;
    private final ParallelizationWorkflow parallelizationWorkflow;
    private final SummaryAgent summaryAgent;

    public ChainWorkflow(InterpretationAgent interpretationAgent,
            PlanningAgent planningAgent,
            ParallelizationWorkflow parallelizationWorkflow,
            SummaryAgent summaryAgent) {
        this.interpretationAgent = interpretationAgent;
        this.planningAgent = planningAgent;
        this.parallelizationWorkflow = parallelizationWorkflow;
        this.summaryAgent = summaryAgent;
    }

    public String run(String codeChanges, String codeAnalysis) {
        log.info("Step 1/4 — InterpretationAgent");
        String codeInterpretation = interpretationAgent.execute(new InterpretationInput(codeChanges));

        log.info("Step 2/4 — PlanningAgent");
        PlanningOutput planningOutput = planningAgent.execute(new PlanningInput(codeInterpretation, codeAnalysis));
        List<String> checklistItems = splitChecklist(planningOutput);
        log.info("Checklist produced: {} items", checklistItems.size());

        log.info("Step 3/4 — Executing checklist ({} items)", checklistItems.size());
        List<String> itemAnswers = parallelizationWorkflow.run(codeChanges, checklistItems);

        log.info("Step 4/4 — SummaryAgent");
        return summaryAgent.execute(new SummaryInput(codeChanges, codeAnalysis, itemAnswers, checklistItems));
    }

    private List<String> splitChecklist(PlanningOutput planningOutput) {
        if (planningOutput == null || planningOutput.checklistItems() == null) {
            return List.of();
        }
        return planningOutput.checklistItems().stream()
                .filter(Objects::nonNull)
                .flatMap(this::splitIfCombinedBlock)
                .map(String::trim)
                .map(s -> s.replaceFirst("^[-*]\\s+", ""))
                .map(s -> s.replaceFirst("^\\d+[.)]\\s+", ""))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private Stream<String> splitIfCombinedBlock(String item) {
        String trimmed = item.trim();
        return trimmed.contains("\n") ? trimmed.lines() : Stream.of(trimmed);
    }
}
