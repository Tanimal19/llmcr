package com.llmcr.agent;

import com.llmcr.agent.ComputationAgent.ComputationAgentOutput;
import com.llmcr.agent.base.SingleCallAgent;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.service.review.CodeReviewService.CodeChange;
import java.util.List;
import java.util.Map;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
public class SummaryAgent extends SingleCallAgent<SummaryAgent.SummaryAgentInput, SummaryAgent.SummaryAgentOutput> {

    public record ItemAnswer(String checklistItemTitle, ComputationAgentOutput answer) {}

    public record SummaryAgentInput(List<CodeChange> codeChanges, String codeAnalysis, List<ItemAnswer> itemAnswers) {}

    public record Issue(String title, String detail, String location, String type) {}

    public record ImplementationDetails(String filename, List<String> details) {}

    public record SummaryAgentOutput(
        String motivation,
        List<String> goodPoints,
        List<String> badPoints,
        String suggestion,
        List<ImplementationDetails> implementationDetails,
        List<Issue> issues
    ) {}

    private static final String SYSTEM_PROMPT =
        """
        You are now a senior reviewer writing a final code review report.
        Your task is to write a comprehensive code review report based on the code change, static analysis results, and checklist item answers provided by the junior reviewer. The report will be used by the author to understand the review feedback and improve the code change.

        The review report should include:
        - Motivation: Why the code change was made, and what original problem it tries to solve.
        - Good points & Bad points: Good points are the aspects that are well done in the code change, while bad points are areas that could be improved but not critical enough to be raised as issues.
        - Suggestion: based on the bad points, provide concrete suggestions for improvement.
        - Implementation details: Summarize important implementation details that reviewers should pay attention to, such as pattern used, non-obvious design decisions, etc. Group the details by file.
        - Issues: Potential problems in the code change. Each issue should have a title, detailed description, type, and a location (file and line number).

        Be concise and specific in your report. Avoid vague and general statements. Focus on providing actionable feedback that can help the author improve the code change.

        You will be given code changes, static analysis results, and checklist item answers. You should make use of all provided information to write a comprehensive review report. Avoid making any assumption beyond the provided information.

        Think step by step internally before generating the code review report.
        """;

    private static final String INITIAL_USER_MESSAGE_TEMPLATE =
        """
        Code changes:
        <code_changes>

        Code analysis:
        <code_analysis>

        Checklist item answers:
        <item_answers>

        <format_instructions>
        """;
    private static final String AGENT_NAME = "summary";

    public SummaryAgent(ApplicationProperties applicationProperties, ModelClientFactory modelClientFactory) {
        super(
            AGENT_NAME,
            applicationProperties,
            modelClientFactory,
            new BeanOutputConverter<>(SummaryAgentOutput.class)
        );
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
    protected Map<String, Object> buildInputVariables(SummaryAgentInput input) {
        String codeChangesText = String.join(
            "\n----\n",
            input
                .codeChanges()
                .stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList()
        );

        String itemAnswersText = String.join(
            "\n----\n",
            input
                .itemAnswers()
                .stream()
                .map(answer -> "ItemTitle: " + answer.checklistItemTitle() + "\nAnswer:\n" + answer.answer().toString())
                .toList()
        );

        return Map.of(
            "code_changes",
            codeChangesText,
            "code_analysis",
            input.codeAnalysis() != null ? input.codeAnalysis() : "(not available)",
            "item_answers",
            itemAnswersText
        );
    }
}
