package com.llmcr.service.review.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.llmcr.model.LargeChatClient;
import com.llmcr.model.advisor.RAGAdvisor;

@Component
public class InterpretationAgent implements AgentInvokeStrategy<InterpretationAgent.InterpretationInput, String> {

    public record InterpretationInput(String codeChanges) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a senior software engineer performing a code review.
            You will be given a set of code changes (diff) and relevant project context.
            Your task is to write a clear, concise interpretation of what the code changes do,
            covering intent, key logic changes, and potential impact on the existing system.
            Be factual and precise. Do not evaluate quality yet — just interpret.
            """;

    private static final int RAG_TOP_K = 5;
    private static final String COLLECTION = "project-context";

    private final LargeChatClient largeChatClient;
    private final Agent<InterpretationInput, String> agent;

    public InterpretationAgent(LargeChatClient largeChatClient, RAGAdvisor ragAdvisor,
            AgentStepLogger agentStepLogger) {
        this.largeChatClient = largeChatClient;
        this.agent = new Agent<>(this, ragAdvisor, agentStepLogger);
    }

    public String execute(InterpretationInput input) {
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
    public String parseInput(InterpretationInput input) {
        return """
                ## Code Changes
                %s

                ## Task
                Interpret the code changes above.
                """.formatted(input.codeChanges());
    }

    @Override
    public String parseOutput(ChatClient.CallResponseSpec response) {
        return response.content();
    }
}
