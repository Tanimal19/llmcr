package com.llmcr.service.review.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.llmcr.model.LargeChatClient;
import com.llmcr.service.rag.ContextRetriever;
import com.llmcr.service.rag.ContextRetriever.RetrievalConfiguration;
import com.llmcr.service.rag.select.FixedKStrategy;

@Component
public class PlanningAgent extends Agent<PlanningAgent.PlanningInput, PlanningAgent.PlanningOutput> {

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
    private final ContextRetriever contextRetriever;

    public PlanningAgent(LargeChatClient largeChatClient, ContextRetriever contextRetriever) {
        this.largeChatClient = largeChatClient;
        this.contextRetriever = contextRetriever;
    }

    @Override
    protected ChatClient chatClient() {
        return largeChatClient.getChatClient();
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected String parseInput(PlanningInput input) {
        RetrievalConfiguration config = new RetrievalConfiguration(
                RAG_TOP_K, COLLECTION, true, new FixedKStrategy());
        List<ContextRetriever.ContextScorePair> retrieved = contextRetriever
                .retrieve(List.of(input.codeInterpretation()), config);

        String guidelinesText = retrieved.stream()
                .map(pair -> pair.context().getContent())
                .reduce((a, b) -> a + "\n\n---\n\n" + b)
                .orElse("");

        String analysisSection = (input.codeAnalysis() == null || input.codeAnalysis().isBlank())
                ? "(no static analysis provided)"
                : input.codeAnalysis();

        return """
                ## Review Guidelines (retrieved)
                %s

                ## Code Interpretation
                %s

                ## Static Code Analysis
                %s

                ## Task
                Produce the review checklist.
                """.formatted(guidelinesText, input.codeInterpretation(), analysisSection);
    }

    @Override
    protected PlanningOutput parseOutput(ChatClient.CallResponseSpec response) {
        return response.entity(PlanningOutput.class);
    }
}
