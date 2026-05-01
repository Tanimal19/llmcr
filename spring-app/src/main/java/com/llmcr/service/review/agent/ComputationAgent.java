package com.llmcr.service.review.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ComputationAgent
        extends Agent<ComputationAgent.ComputationInput, ComputationAgent.ComputationDecision> {

    private static final Logger log = LoggerFactory.getLogger(ComputationAgent.class);

    public record ComputationInput(String codeChanges, String checklistItem, String additionalContext) {
        public ComputationInput(String codeChanges, String checklistItem) {
            this(codeChanges, checklistItem, "");
        }
    }

    public record ComputationDecision(
            boolean needsMoreData,
            String dataQuery,
            String answer) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a senior code reviewer answering a specific checklist item.
            You will be given the code changes and, optionally, additional retrieved context.

            Determine whether you have enough information to answer the checklist item.
            If yes, answer it directly and concisely.
            If you need more data, specify a precise query to retrieve it.

            Respond ONLY with a JSON object in this exact format:
            {
              "needsMoreData": <true|false>,
              "dataQuery": "<precise query string if needsMoreData is true, otherwise empty string>",
              "answer": "<your answer if needsMoreData is false, otherwise empty string>"
            }
            """;

    private final com.llmcr.model.SmallChatClient smallChatClient;

    public ComputationAgent(com.llmcr.model.SmallChatClient smallChatClient) {
        this.smallChatClient = smallChatClient;
    }

    @Override
    protected ChatClient chatClient() {
        return smallChatClient.getChatClient();
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected String parseInput(ComputationInput input) {
        String contextSection = (input.additionalContext() == null || input.additionalContext().isBlank())
                ? "(no additional context)"
                : input.additionalContext();

        return """
                ## Code Changes
                %s

                ## Additional Context
                %s

                ## Checklist Item to Answer
                %s
                """.formatted(input.codeChanges(), contextSection, input.checklistItem());
    }

    @Override
    protected ComputationDecision parseOutput(ChatClient.CallResponseSpec response) {
        ComputationDecision decision = response.entity(ComputationDecision.class);

        if (decision == null) {
            log.warn("[ComputationAgent] Null decision returned by model");
        }
        return decision;
    }
}
