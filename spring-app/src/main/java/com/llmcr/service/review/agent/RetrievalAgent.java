package com.llmcr.service.review.agent;

import org.springframework.stereotype.Component;

import com.llmcr.model.SmallChatClient;
import com.llmcr.model.advisor.RAGAdvisor;
import com.llmcr.service.review.tool.RetrievalTools;

@Component
public class RetrievalAgent implements AgentInvokeStrategy<RetrievalAgent.RetrievalInput, String> {

    public record RetrievalInput(String dataQuery) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a data retrieval assistant.
            Your goal is to gather enough information to satisfy the user's data query.
            Use the available tools when you need project context or when the request is missing information that only the user can provide.

            Rules:
            - Prefer retrieving context before asking the user, unless the answer truly depends on missing user-specific details.
            - Use only the information from the conversation and tool results.
            - Give a concise final answer that directly addresses the data query.
            - If the available context is insufficient, say what is still missing.
            """;

    private final SmallChatClient smallChatClient;
    private final RetrievalTools retrievalTools;
    private final Agent<RetrievalInput, String> agent;

    public RetrievalAgent(SmallChatClient smallChatClient, RetrievalTools retrievalTools,
            RAGAdvisor ragAdvisor, AgentStepLogger agentStepLogger) {
        this.smallChatClient = smallChatClient;
        this.retrievalTools = retrievalTools;
        this.agent = new Agent<>(this, ragAdvisor, agentStepLogger);
    }

    public String execute(RetrievalInput input) {
        return agent.execute(input);
    }

    @Override
    public org.springframework.ai.chat.client.ChatClient chatClient() {
        return smallChatClient.getChatClient();
    }

    @Override
    public Object[] tools(RetrievalInput input) {
        return new Object[] { retrievalTools };
    }

    @Override
    public String parseInput(RetrievalInput input) {
        return """
                ## Data Query
                %s

                ## Task
                Answer the data query using the available tools when needed.
                """.formatted(input.dataQuery());
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String parseOutput(org.springframework.ai.chat.client.ChatClient.CallResponseSpec response) {
        return response.content();
    }
}
