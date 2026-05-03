package com.llmcr.service.review.agent;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.llmcr.model.LargeChatClient;
import com.llmcr.model.advisor.RAGAdvisor;

@Component
public class SummaryAgent implements AgentInvokeStrategy<SummaryAgent.SummaryInput, String> {

    public record SummaryInput(String codeChanges, String codeAnalysis, List<String> itemAnswers,
            List<String> checklistItems) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a senior software engineer writing the final code review report.
            You will be given:
            - The code changes under review.
            - Optional static analysis results.
            - A checklist of review items and the corresponding answers produced by previous analysis.

            Write a comprehensive, well-structured code review report that:
            1. Summarises the purpose and impact of the changes.
            2. Highlights issues found, grouped by severity (Critical / Major / Minor).
            3. Calls out positive aspects of the implementation.
            4. Provides a clear overall verdict (Approve / Request Changes / Needs Discussion).

            Be specific, constructive, and professional.
            """;

    private final LargeChatClient largeChatClient;
    private final Agent<SummaryInput, String> agent;

    public SummaryAgent(LargeChatClient largeChatClient, RAGAdvisor ragAdvisor,
            AgentStepLogger agentStepLogger) {
        this.largeChatClient = largeChatClient;
        this.agent = new Agent<>(this, ragAdvisor, agentStepLogger);
    }

    public String execute(SummaryInput input) {
        return agent.execute(input);
    }

    @Override
    public ChatClient chatClient() {
        return largeChatClient.getChatClient();
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String parseInput(SummaryInput input) {
        String analysisSection = (input.codeAnalysis() == null || input.codeAnalysis().isBlank())
                ? "(no static analysis provided)"
                : input.codeAnalysis();

        String checklistAnswers = IntStream.range(0, input.checklistItems().size())
                .mapToObj(i -> "- **" + input.checklistItems().get(i) + "**\n  " + input.itemAnswers().get(i))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        return """
                ## Code Changes
                %s

                ## Static Code Analysis
                %s

                ## Checklist Answers
                %s

                ## Task
                Write the code review report.
                """.formatted(input.codeChanges(), analysisSection, checklistAnswers);
    }

    @Override
    public String parseOutput(ChatClient.CallResponseSpec response) {
        return response.content();
    }
}
