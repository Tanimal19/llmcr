package com.llmcr.service.review;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.ComputationAgent;
import com.llmcr.service.review.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.service.review.agent.ComputationAgent.ComputationAgentOutput;
import com.llmcr.service.review.agent.InterpretationAgent;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.service.review.agent.PlanningAgent;
import com.llmcr.service.review.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.service.review.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.service.review.agent.RetrievalAgent;
import com.llmcr.service.review.agent.RetrievalAgent.RetrievalAgentInput;
import com.llmcr.service.review.agent.RetrievalAgent.RetrievalAgentOutput;
import com.llmcr.service.review.agent.RetrievalAgent.ToolRequest;
import com.llmcr.service.review.agent.SummaryAgent;
import com.llmcr.service.review.agent.SummaryAgent.ItemAnswer;
import com.llmcr.service.review.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.service.review.agent.SummaryAgent.SummaryAgentOutput;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.UserInteractionTool;
import com.llmcr.util.GitDiffParser.CodeChange;

@Component
public class ChainWorkflow {

    public record ReviewResult(
            InterpretationAgentOutput interpretation,
            SummaryAgentOutput summary) {
    }

    private static final Logger log = LoggerFactory.getLogger(ChainWorkflow.class);

    private static final int COMPUTATION_MAX_ITER = 3;
    private static final int RETRIEVAL_MAX_ITER = 5;
    private static final int RETRIEVAL_ASK_USER_AFTER = 3;

    private final InterpretationAgent interpretationAgent;
    private final PlanningAgent planningAgent;
    private final ComputationAgent computationAgent;
    private final RetrievalAgent retrievalAgent;
    private final SummaryAgent summaryAgent;
    private final UserInteractionTool userInteractionTool;
    private final DatabaseTool databaseTool;

    public ChainWorkflow(
            InterpretationAgent interpretationAgent,
            PlanningAgent planningAgent,
            ComputationAgent computationAgent,
            RetrievalAgent retrievalAgent,
            SummaryAgent summaryAgent,
            UserInteractionTool userInteractionTool,
            DatabaseTool databaseTool) {
        this.interpretationAgent = interpretationAgent;
        this.planningAgent = planningAgent;
        this.computationAgent = computationAgent;
        this.retrievalAgent = retrievalAgent;
        this.summaryAgent = summaryAgent;
        this.userInteractionTool = userInteractionTool;
        this.databaseTool = databaseTool;
    }

    public ReviewResult run(List<CodeChange> codeChanges, String codeAnalysis) {
        log.info("step=interpretation");
        InterpretationAgentOutput interpretation = interpretationAgent.execute(
                new InterpretationAgentInput(codeChanges));

        log.info("step=planning");
        PlanningAgentOutput planning = planningAgent.execute(
                new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));

        log.info("step=computation items={}", planning.checklistItems().size());
        List<ItemAnswer> itemAnswers = runParallelization(codeChanges, planning.checklistItems());

        log.info("step=summary");
        SummaryAgentOutput summary = summaryAgent.execute(
                new SummaryAgentInput(codeChanges, codeAnalysis, itemAnswers));

        return new ReviewResult(interpretation, summary);
    }

    // --- Parallelization ---

    private List<ItemAnswer> runParallelization(List<CodeChange> codeChanges, List<String> checklistItems) {
        List<ItemAnswer> answers = new ArrayList<>();
        List<String> safeItems = checklistItems == null ? List.of() : checklistItems;
        for (String item : safeItems) {
            log.debug("item={}", item);
            answers.add(runChecklistItem(codeChanges, item));
        }
        return answers;
    }

    // --- Checklist Item Orchestration ---

    private ItemAnswer runChecklistItem(List<CodeChange> codeChanges, String checklistItem) {
        String previousAnalysis = null;
        String retrievalResult = null;

        for (int i = 0; i < COMPUTATION_MAX_ITER; i++) {
            ComputationAgentOutput output = computationAgent.execute(
                    new ComputationAgentInput(codeChanges, checklistItem, previousAnalysis, retrievalResult));

            if (!output.needsAdditionalData()) {
                return new ItemAnswer(checklistItem, output.answer());
            }

            log.debug("item={} retrieval query={}", checklistItem, output.dataQuery());
            retrievalResult = runRetrieval(output.dataQuery());
            previousAnalysis = output.answer();
        }

        ComputationAgentOutput finalOutput = computationAgent.execute(
                new ComputationAgentInput(codeChanges, checklistItem, previousAnalysis, retrievalResult));
        return new ItemAnswer(checklistItem, finalOutput.answer());
    }

    // --- Retrieval Loop ---

    private String runRetrieval(String dataQuery) {
        String currentQuery = dataQuery;
        List<String> toolResponses = new ArrayList<>();

        for (int i = 0; i < RETRIEVAL_MAX_ITER; i++) {
            if (i >= RETRIEVAL_ASK_USER_AFTER) {
                ToolRequest fallback = new ToolRequest(
                        "askUserQuestion",
                        Map.of("question", currentQuery),
                        "fallback: retrieval did not satisfy the query after " + i + " attempts");
                toolResponses.add("[" + fallback.purpose() + "]\n" + dispatchTool(fallback));
                break;
            }

            RetrievalAgentOutput output = retrievalAgent.execute(
                    new RetrievalAgentInput(currentQuery, List.copyOf(toolResponses)));

            if (output.satisfied() || output.toolRequests() == null || output.toolRequests().isEmpty()) {
                break;
            }

            for (ToolRequest req : output.toolRequests()) {
                toolResponses.add("[" + req.purpose() + "]\n" + dispatchTool(req));
            }

            if (output.refinedQuery() != null && !output.refinedQuery().isBlank()) {
                currentQuery = output.refinedQuery();
            }
        }

        return String.join("\n----\n", toolResponses);
    }
}
