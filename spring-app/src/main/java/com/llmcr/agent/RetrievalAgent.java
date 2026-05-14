package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.service.ModelClientFactory;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.UserInteractionTool;

@Component
public class RetrievalAgent extends SingleCallAgent<String, String> {

    private static final String SYSTEM_PROMPT = """
            You are a retrieval assistant.
            Your task is to answer the user's data query by calling tools.
            You should only use the provided tools to get information relevant to the user's query, and then answer the query based on the retrieved information.
            If all tools tried and you still cannot find relevant information to answer the query, it's okay to say "I couldn't find relevant information to answer the query" rather than making up an answer.
            """;

    private static final String PROMPT_TEMPLATE = """
            User query: <query>
            """;

    private final ToolCallbackProvider toolProvider;

    public RetrievalAgent(
            @Value("${llmcr.agent.retrieval.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.retrieval.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            UserInteractionTool userInteractionTool, DatabaseTool databaseTool) {
        super(modelClientFactory.createChatClient(chatProviderName, chatModelName), null);

        toolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(List.of(databaseTool, userInteractionTool).toArray())
                .build();
    }

    @Override
    protected String getPromptTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    protected Map<String, Object> getPromptVariables(String input) {
        return Map.of("query", input);
    }

    @Override
    protected ChatClientRequestSpec customizeRequest(ChatClientRequestSpec requestSpec) {
        return requestSpec.system(SYSTEM_PROMPT).toolCallbacks(toolProvider);
    }

}