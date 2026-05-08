package com.llmcr.service.review.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import com.llmcr.agent.Agent;
import com.llmcr.agent.AgentInput;
import com.llmcr.client.ChatClientWrapper;
import com.llmcr.client.LargeChatClient;
import com.llmcr.rag.retrieval.QueryContextRetriever;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.rag.retrieval.select.AdaptiveKStrategy;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class PlanningAgent
        extends
        Agent<PlanningAgent.PlanningAgentInput, PlanningAgent.PlanningAgentOutput, PlanningAgent.PlanningAgentOutput> {

    public record PlanningAgentInput(
            List<CodeChange> codeChanges,
            InterpretationAgentOutput codeInterpretation,
            String codeAnalysis) implements AgentInput {

        @Override
        public List<String> buildQueries() {
            return List.of(
                    StringUtils.safeText(codeInterpretation.changeDescription()),
                    StringUtils.safeText(codeInterpretation.changeMotivation()),
                    StringUtils.safeText(codeAnalysis));
        }

        @Override
        public Map<String, Object> getTemplateVariables() {
            List<CodeChange> safeCodeChanges = codeChanges == null ? List.of() : codeChanges;
            String codeChangesText = String.join("\n----\n", safeCodeChanges.stream()
                    .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                    .toList());

            return Map.of(
                    "code_changes", codeChangesText,
                    "change_description",
                    StringUtils.safeText(codeInterpretation.changeDescription()) + "\n"
                            + StringUtils.safeText(codeInterpretation.changeMotivation()),
                    "code_analysis", StringUtils.safeText(codeAnalysis));
        }
    }

    public record PlanningAgentOutput(List<String> checklistItems) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are now a software engineer experienced at Java and Spring Framework. Your task is to generate a checklist to be check in the code review. Here's the things you need to consider but not limited to:
            - The compatibility of the code change, does it fit with existing code and intended usage scenarios?
            - The design of the code change, is it well-structured and following best practices?
            - The security implications of the code change, does it introduce any vulnerabilities?
            - The functionality of the code change, is it working as intended?
            - The performance impact of the code change, does it introduce any inefficiencies?
            - The maintainability of the code change, is it easy to understand and modify in the future?
            - The readability of the code change, is it clear and well-documented?

            You will be given a list of code changes, an interpretation of the change, and outputs of static analysis tools.

            Based on the given information, generate a checklist for code review. Each checklist item should be a concise question focusing on one specific aspect to check. Avoid vague or open-ended items. Plan 5 ~ 8 items.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            Below is the code change:
            <code_changes>

            Below is the change description:
            <change_description>

            Below is the code analysis:
            <code_analysis>
            """;

    private static final String CONTEXT_MESSAGE_TEMPLATE = """
            Below is a list of review guideline to be used as reference when creating the checklist:
            <context>
            """;

    private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
            10, new AdaptiveKStrategy(), "guidelines", false);

    private final LargeChatClient chatClient;

    public PlanningAgent(LargeChatClient chatClient, QueryContextRetriever queryContextRetriever) {
        super(
                queryContextRetriever,
                RETRIEVAL_CONFIGURATION,
                1,
                null,
                true, false, true,
                SYSTEM_MESSAGE,
                CONTEXT_MESSAGE_TEMPLATE,
                USER_MESSAGE_TEMPLATE);
        this.chatClient = chatClient;
    }

    @Override
    protected Class<PlanningAgentOutput> modelOutputClass() {
        return PlanningAgentOutput.class;
    }

    @Override
    protected ChatClientWrapper chatClient() {
        return chatClient;
    }

    @Override
    protected PlanningAgentOutput constructAgentOutput(
            ResponseEntity<ChatResponse, PlanningAgentOutput> responseEntity) {
        return responseEntity.entity();
    }

}
