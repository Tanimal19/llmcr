package com.llmcr.service.review.workflow;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.InterpretationAgent;
import com.llmcr.service.review.agent.PlanningAgent;
import com.llmcr.service.review.agent.SummaryAgent;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.service.review.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.service.review.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.service.review.agent.SummaryAgent.ItemAnswer;
import com.llmcr.service.review.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.service.review.agent.SummaryAgent.SummaryAgentOutput;
import com.llmcr.util.GitDiffParser.CodeChange;

/**
 * Top-level chain workflow for the full code review pipeline.
 *
 * <p>
 * Steps executed in order:
 * <ol>
 * <li><b>Interpretation</b> – understand what changed and why.</li>
 * <li><b>Planning</b> – derive a concrete review checklist from the
 * interpretation.</li>
 * <li><b>Computation</b> – evaluate every checklist item via
 * {@link ParallelizationWorkflow}.</li>
 * <li><b>Summary</b> – aggregate all item answers into the final review
 * report.</li>
 * </ol>
 */
@Component
public class ChainWorkflow {

    public record ReviewResult(
            InterpretationAgentOutput interpretation,
            SummaryAgentOutput summary) {
    }

    private static final Logger log = LoggerFactory.getLogger(ChainWorkflow.class);

    private final InterpretationAgent interpretationAgent;
    private final PlanningAgent planningAgent;
    private final ParallelizationWorkflow parallelizationWorkflow;
    private final SummaryAgent summaryAgent;

    public ChainWorkflow(
            InterpretationAgent interpretationAgent,
            PlanningAgent planningAgent,
            ParallelizationWorkflow parallelizationWorkflow,
            SummaryAgent summaryAgent) {
        this.interpretationAgent = interpretationAgent;
        this.planningAgent = planningAgent;
        this.parallelizationWorkflow = parallelizationWorkflow;
        this.summaryAgent = summaryAgent;
    }

    /**
     * Run the complete code review chain for the given code changes.
     *
     * @param codeChanges  parsed diff entries to review.
     * @param codeAnalysis optional pre-existing static analysis output (may be
     *                     null).
     * @return a {@link ReviewResult} containing the interpretation and final
     *         summary report.
     */
    public ReviewResult run(List<CodeChange> codeChanges, String codeAnalysis) {
        log.info("step=interpretation");
        InterpretationAgentOutput interpretation = interpretationAgent.execute(
                new InterpretationAgentInput(codeChanges));
        log.info("Interpretation output: {}", interpretation);

        log.info("step=planning");
        PlanningAgentOutput planning = planningAgent.execute(
                new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
        log.info("Planning output: {}", planning);

        // log.info("step=computation items={}", planning.checklistItems().size());
        // List<ItemAnswer> itemAnswers = parallelizationWorkflow.run(
        // codeChanges, planning.checklistItems());

        // log.info("step=summary");
        // SummaryAgentOutput summary = summaryAgent.execute(
        // new SummaryAgentInput(codeChanges, codeAnalysis, itemAnswers));

        return new ReviewResult(interpretation, null);
    }
}
