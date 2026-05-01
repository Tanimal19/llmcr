package com.llmcr.service.review.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.llmcr.model.SmallChatClient;
import com.llmcr.service.review.tool.RetrievalTools;

@Component
public class RetrievalAgent extends Agent<RetrievalAgent.RetrievalInput, String> {

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

    public RetrievalAgent(SmallChatClient smallChatClient, RetrievalTools retrievalTools) {
        this.smallChatClient = smallChatClient;
        this.retrievalTools = retrievalTools;
    }

    @Override
    protected ChatClient chatClient() {
        return smallChatClient.getChatClient();
    }

    @Override
    public String execute(RetrievalInput input) {
        return smallChatClient.getChatClient()
                .prompt()
                .system(systemPrompt())
                .user(parseInput(input))
                .tools(retrievalTools)
                .call()
                .content();
    }

    @Override
    protected String parseInput(RetrievalInput input) {
        return """
                ## Data Query
                %s

                ## Task
                Answer the data query using the available tools when needed.
                """.formatted(input.dataQuery());
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected String parseOutput(org.springframework.ai.chat.client.ChatClient.CallResponseSpec response) {
        return response.content();
    }
}
