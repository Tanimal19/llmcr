package com.llmcr.service.review.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.llmcr.model.LargeChatClient;
import com.llmcr.model.advisor.RAGAdvisor;

@Component
public class PlanningAgent implements AgentInvokeStrategy<PlanningAgent.PlanningInput, PlanningAgent.PlanningOutput> {

    public record PlanningInput(String codeInterpretation, String codeAnalysis) {
    }

    public record PlanningOutput(List<String> checklistItems) {
    }

    private static final String SYSTEM_PROMPT = """
            You are an expert code reviewer.
            You will be given:
            - A code interpretation describing what the changes do.
            - Optional static analysis results.
            - Relevant code review guidelines retrieved from a knowledge base.

            Your task is to produce a numbered checklist of concrete items that must be verified
            during the review. Each item should be a single, actionable check.
            Return ONLY the checklist items as a JSON object in this exact format:
            {"checklistItems": ["item 1", "item 2", ...]}
            """;

    private static final int RAG_TOP_K = 5;
    private static final String COLLECTION = "guidelines";

    private final LargeChatClient largeChatClient;
    private final Agent<PlanningInput, PlanningOutput> agent;

    public PlanningAgent(LargeChatClient largeChatClient, RAGAdvisor ragAdvisor,
            AgentStepLogger agentStepLogger) {
        this.largeChatClient = largeChatClient;
        this.agent = new Agent<>(this, ragAdvisor, agentStepLogger);
    }

    public PlanningOutput execute(PlanningInput input) {
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
    public Integer ragTopK() {
        return RAG_TOP_K;
    }

    @Override
    public String ragCollectionName() {
        return COLLECTION;
    }

    @Override
    public String parseInput(PlanningInput input) {
        String analysisSection = (input.codeAnalysis() == null || input.codeAnalysis().isBlank())
                ? "(no static analysis provided)"
                : input.codeAnalysis();

        return """
                ## Code Interpretation
                %s

                ## Static Code Analysis
                %s

                ## Task
                Produce the review checklist.
                """.formatted(input.codeInterpretation(), analysisSection);
    }

    @Override
    public PlanningOutput parseOutput(ChatClient.CallResponseSpec response) {
        return response.entity(PlanningOutput.class);
    }
}
