package com.llmcr.service.review.agent.summary;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.llmcr.agent.AgentInput;
import com.llmcr.client.ChatClientWrapper;
import com.llmcr.client.LargeChatClient;
import com.llmcr.service.review.agent.BaseReviewAgent;
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class SummaryAgent extends BaseReviewAgent<SummaryAgent.SummaryAgentInput, SummaryAgent.SummaryAgentOutput> {

    public record ItemAnswer(String checklistItemId, String checklistItemTitle, String answer, String confidence) {
    }

    public record SummaryAgentInput(
            List<CodeChange> codeChanges,
            String codeAnalysis,
            List<ItemAnswer> itemAnswers) implements AgentInput {

        @Override
        public Map<String, Object> getTemplateVariables() {
            List<CodeChange> safeCodeChanges = codeChanges == null ? List.of() : codeChanges;
            String codeChangesText = String.join("\n----\n", safeCodeChanges.stream()
                    .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                    .toList());

            List<ItemAnswer> safeItemAnswers = itemAnswers == null ? List.of() : itemAnswers;
            String itemAnswersText = String.join("\n----\n", safeItemAnswers.stream()
                    .map(answer -> "ItemId: " + answer.checklistItemId()
                            + "\nItemTitle: " + answer.checklistItemTitle()
                            + "\nConfidence: " + answer.confidence()
                            + "\nAnswer: " + answer.answer())
                    .toList());

            return Map.of(
                    "code_changes", codeChangesText,
                    "code_analysis", StringUtils.safeText(codeAnalysis),
                    "item_answers", itemAnswersText);
        }
    }

    public record Finding(String title, String detail, String severity, String filePath) {
    }

    public record SummaryAgentOutput(
            String summary,
            List<Finding> findings,
            List<String> risks,
            String overallVerdict) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are now a senior reviewer writing a final code review report.
            Aggregate checklist answers into a concise summary and explicit findings.
            Severity should be one of: CRITICAL, HIGH, MEDIUM, LOW, INFO.
            Avoid inventing facts not present in the given input.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            Code changes:
            <code_changes>

            Code analysis:
            <code_analysis>

            Checklist item answers:
            <item_answers>
            """;

    private final LargeChatClient chatClient;

    public SummaryAgent(LargeChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Class<SummaryAgentOutput> outputClass() {
        return SummaryAgentOutput.class;
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