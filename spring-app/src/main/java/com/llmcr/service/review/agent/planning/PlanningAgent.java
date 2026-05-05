package com.llmcr.service.review.agent.planning;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.llmcr.agent.AgentInput;
import com.llmcr.client.ChatClientWrapper;
import com.llmcr.client.LargeChatClient;
import com.llmcr.service.rag.RAGAdvisor;
import com.llmcr.service.rag.RAGInput;
import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.retrieval.select.AdaptiveKStrategy;
import com.llmcr.service.review.agent.BaseReviewAgent;
import com.llmcr.service.review.agent.interpretation.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class PlanningAgent
        extends BaseReviewAgent<PlanningAgent.PlanningAgentInput, PlanningAgent.PlanningAgentOutput> {

    public record PlanningAgentInput(
            List<CodeChange> codeChanges,
            InterpretationAgentOutput codeInterpretation,
            String codeAnalysis) implements AgentInput, RAGInput {

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

    public record ChecklistItem(String id, String description) {
    }

    public record PlanningAgentOutput(List<ChecklistItem> checklistItems) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are now a senior software engineer specializing in Java and Spring code review.
            Your task is to create a practical review checklist for the given code change.
            You will be given a code change, a description of the change, and an analysis of the change.
            Keep checklist items concrete and verifiable. You should produce at most 10 checklist items, each item should be concise (1-2 sentences) and focus on one specific aspect to check.
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

    public PlanningAgent(LargeChatClient chatClient, RAGAdvisor.Builder ragAdvisorBuilder) {
        this.chatClient = chatClient;
        super.advisors.add(ragAdvisorBuilder
                .retrievalConfiguration(RETRIEVAL_CONFIGURATION)
                .messageTemplate(CONTEXT_MESSAGE_TEMPLATE)
                .build());
    }

    @Override
    public Class<PlanningAgentOutput> outputClass() {
        return PlanningAgentOutput.class;
    }

    @Override
    protected String agentName() {
        return this.getClass().getSimpleName();
    }

    @Override
    protected String clientType() {
        return this.chatClient.getClass().getSimpleName();
    }

    @Override
    protected void preprocess(PlanningAgentInput input) {
        super.preprocess(input);
        super.advisorParams.put(RAGAdvisor.RAG_INPUT, input);
    }

    @Override
    public ChatClientWrapper chatClient() {
        return chatClient;
    }

    @Override
    public String systemMessage() {
        return SYSTEM_MESSAGE;
    }

    @Override
    public String userMessageTemplate() {
        return USER_MESSAGE_TEMPLATE;
    }

}