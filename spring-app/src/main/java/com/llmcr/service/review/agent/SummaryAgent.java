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
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class SummaryAgent extends
        Agent<SummaryAgent.SummaryAgentInput, SummaryAgent.SummaryAgentOutput, SummaryAgent.SummaryAgentOutput> {

    public record ItemAnswer(String checklistItemTitle, String answer) {
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
                    .map(answer -> "ItemTitle: " + answer.checklistItemTitle()
                            + "\nAnswer: " + answer.answer())
                    .toList());

            return Map.of(
                    "code_changes", codeChangesText,
                    "code_analysis", StringUtils.safeText(codeAnalysis),
                    "item_answers", itemAnswersText);
        }
    }

    public record Issue(String title, String detail, String location, String type, String severity) {
    }

    public record SummaryAgentOutput(
            String summary,
            List<String> goodPoints,
            List<String> badPoints,
            List<Issue> issues,
            String overallVerdict) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are now a senior reviewer writing a final code review report.
            Your task is to write a final review including a concise summary, good points, bad points, and potential issues. Each issue should have a title, detailed description, location (file and line number), type (e.g., bug, code smell, security ...) and severity level (low, medium, high). Finally give an overall verdict of the code change (e.g., approve, request changes, etc.).

            You will be given the code change, code analysis, and checklist item answers. You should make use of all provided information to write a comprehensive review report. Avoid making any assumption beyond the provided information.
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
        super(
                null,
                null,
                1,
                null,
                false, false, true,
                SYSTEM_MESSAGE,
                "",
                USER_MESSAGE_TEMPLATE);
        this.chatClient = chatClient;
    }

    @Override
    protected Class<SummaryAgentOutput> modelOutputClass() {
        return SummaryAgentOutput.class;
    }

    @Override
    protected ChatClientWrapper chatClient() {
        return chatClient;
    }

    @Override
    protected SummaryAgentOutput constructAgentOutput(ResponseEntity<ChatResponse, SummaryAgentOutput> responseEntity) {
        return responseEntity.entity();
    }
}
