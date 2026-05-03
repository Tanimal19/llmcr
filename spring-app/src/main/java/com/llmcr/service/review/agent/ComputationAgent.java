package com.llmcr.service.review.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.llmcr.model.advisor.RAGAdvisor;

@Component
public class ComputationAgent
        implements AgentInvokeStrategy<ComputationAgent.ComputationInput, ComputationAgent.ComputationDecision> {

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

    private static final int RAG_TOP_K = 5;
    private static final String COLLECTION = "usecase";

    private final com.llmcr.model.SmallChatClient smallChatClient;
    private final Agent<ComputationInput, ComputationDecision> agent;

    public ComputationAgent(com.llmcr.model.SmallChatClient smallChatClient, RAGAdvisor ragAdvisor,
            AgentStepLogger agentStepLogger) {
        this.smallChatClient = smallChatClient;
        this.agent = new Agent<>(this, ragAdvisor, agentStepLogger);
    }

    public ComputationDecision execute(ComputationInput input) {
        return agent.execute(input);
    }

    @Override
    public ChatClient chatClient() {
        return smallChatClient.getChatClient();
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
    public String parseInput(ComputationInput input) {
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
    public ComputationDecision parseOutput(ChatClient.CallResponseSpec response) {
        ComputationDecision decision = response.entity(ComputationDecision.class);

        if (decision == null) {
            log.warn("[ComputationAgent] Null decision returned by model");
        }
        return decision;
    }
}
