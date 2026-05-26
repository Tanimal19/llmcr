package com.llmcr.feature.review.agent;

import com.llmcr.config.SystemConfig;
import com.llmcr.feature.review.CodeReviewReport.CodeChange;
import com.llmcr.feature.review.CodeReviewReport.InterpretationContent;
import com.llmcr.infrastructure.agent.SingleCallAgent;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import com.llmcr.infrastructure.rag.ContextScorePair;
import com.llmcr.infrastructure.rag.QueryContextRetriever;
import com.llmcr.infrastructure.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.infrastructure.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.infrastructure.rag.select.AdaptiveKStrategy;

import java.util.List;
import java.util.Map;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
public class PlanningAgent
        extends SingleCallAgent<PlanningAgent.PlanningAgentInput, PlanningAgent.PlanningAgentOutput> {

    public record PlanningAgentInput(
            List<CodeChange> codeChanges,
            InterpretationContent codeInterpretation,
            String codeAnalysis) {
    }

    public record PlanningAgentOutput(String innerThought, List<String> checklistItems) {
    }

    private static final String SYSTEM_PROMPT = """
            You are now a software engineer experienced at Java and Spring Framework.

            Your task is to generate a checklist for code review. Here are aspects you should consider, including but not limited to:
            - Compatibility: does the change fit existing code and intended usage scenarios?
            - Design: is the change well-structured and aligned with best practices?
            - Security: does it introduce vulnerabilities?
            - Functionality: does it work as intended?
            - Performance: does it introduce inefficiencies?
            - Maintainability: is it easy to understand and modify later?
            - Readability: is it clear and understandable?

            Create 5 to 8 checklist items. Each item should be a concise question that focuses on one specific aspect to verify. Avoid vague or open-ended items.

            You will be given code changes, a change interpretation, static analysis outputs, and review guidelines. Do not make assumptions beyond the provided information.

            Think step by step internally before answering.
            """;

    private static final String INITIAL_USER_MESSAGE_TEMPLATE = """
            Below is the code change:
            <code_changes>

            Below is the change description:
            <change_description>

            Below is the code analysis:
            <code_analysis>

            Below is a list of review guideline to be used as reference when creating the checklist:
            <context>

            <format_instructions>
            """;

    private static final String AGENT_NAME = "planning";
    private final ContextRetrievalConfiguration retrievalConfiguration;
    private final QueryContextRetriever queryContextRetriever;

    public PlanningAgent(
            SystemConfig applicationProperties,
            ModelClientFactory modelClientFactory,
            QueryContextRetriever queryContextRetriever) {
        super(
                AGENT_NAME,
                applicationProperties,
                modelClientFactory,
                new BeanOutputConverter<>(PlanningAgentOutput.class));
        this.retrievalConfiguration = new ContextRetrievalConfiguration(
                10,
                new AdaptiveKStrategy(),
                applicationProperties.getAgents().get(AGENT_NAME).getCollection(),
                false);
        this.queryContextRetriever = queryContextRetriever;
    }

    @Override
    protected String getSystemMessage() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected String getInitialUserMessageTemplate() {
        return INITIAL_USER_MESSAGE_TEMPLATE;
    }

    @Override
    protected Map<String, Object> buildInputVariables(PlanningAgentInput input) {
        String codeChangesText = String.join(
                "\n----\n",
                input
                        .codeChanges()
                        .stream()
                        .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                        .toList());

        InterpretationContent interpretation = input.codeInterpretation();
        String descriptionText = interpretation.changeMotivation() + "\n" + interpretation.changeDescription();
        String contextText = retrieveContext(input, descriptionText);

        return Map.of(
                "code_changes",
                codeChangesText,
                "change_description",
                descriptionText,
                "code_analysis",
                input.codeAnalysis() != null ? input.codeAnalysis() : "(not available)",
                "context",
                contextText);
    }

    private String retrieveContext(PlanningAgentInput input, String descriptionText) {
        List<String> queries = java.util.stream.Stream.of(descriptionText, input.codeAnalysis())
                .filter(q -> q != null && !q.isBlank())
                .toList();
        List<ContextScorePair> retrievedContexts = queryContextRetriever.retrieve(
                new ContextRetrievalRequest(queries, retrievalConfiguration));
        return String.join("\n---\n", retrievedContexts.stream().map(pair -> pair.context().getContent()).toList());
    }
}
