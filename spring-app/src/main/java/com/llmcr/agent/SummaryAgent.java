package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import com.llmcr.client.LargeChatClient;
import com.llmcr.util.GitDiffParser.CodeChange;

@Component
public class SummaryAgent {

    public record ItemAnswer(String checklistItemTitle, String answer) {
    }

    public record SummaryAgentInput(
            List<CodeChange> codeChanges,
            String codeAnalysis,
            List<ItemAnswer> itemAnswers) {
    }

    public record Issue(String title, String detail, String location, String type) {
    }

    public record ImplementationDetails(String filename, List<String> details) {
    }

    public record SummaryAgentOutput(
            String motivation,
            List<String> goodPoints,
            List<String> badPoints,
            String suggestion,
            List<ImplementationDetails> implementationDetails,
            List<Issue> issues) {
    }

    private static final String PROMPT_TEMPLATE = """
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

            Code changes:
            <code_changes>

            Code analysis:
            <code_analysis>

            Checklist item answers:
            <item_answers>

            Think step by step internally before generating the code review report.
            """;

    private final ChatClient chatClient;
    private final BeanOutputConverter<SummaryAgentOutput> outputConverter;

    public SummaryAgent(LargeChatClient chatClient) {
        this.chatClient = chatClient.getChatClient();
        this.outputConverter = new BeanOutputConverter<>(SummaryAgentOutput.class);
    }

    public SummaryAgentOutput execute(SummaryAgentInput input) {
        String codeChangesText = String.join("\n----\n", input.codeChanges().stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());

        String itemAnswersText = String.join("\n----\n", input.itemAnswers().stream()
                .map(answer -> "ItemTitle: " + answer.checklistItemTitle()
                        + "\nAnswer: " + answer.answer())
                .toList());

        String prompt = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(PROMPT_TEMPLATE)
                .build()
                .render(Map.of(
                        "code_changes", codeChangesText,
                        "code_analysis", input.codeAnalysis() != null ? input.codeAnalysis() : "(not available)",
                        "item_answers", itemAnswersText));
        prompt = prompt + "\n\n" + outputConverter.getFormat();

        ResponseEntity<ChatResponse, SummaryAgentOutput> response = chatClient
                .prompt(prompt)
                .advisors(new SimpleLoggerAdvisor())
                .call()
                .responseEntity(SummaryAgentOutput.class);

        return response.entity();
    }
}
