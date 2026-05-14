package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.SingleCallAgent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.UserInteractionTool;

@Component
public class RetrievalAgent extends SingleCallAgent<String, String> {

    private static final String SYSTEM_PROMPT = """
            You are a retrieval assistant.
            Your task is to retrieve information relevant to the user's query by using the provided tools. The information you retrieve will be used to answer the user's query, so it's important to find as much relevant information as possible.
            You don't need to answer the user's query directly, just find relevant information that could help answer the query. And directly return the retrieved information as the output without any additional explanation.
            NEVER make up any information. ONLY return the information retrieved by tools. If you can't find any relevant information, just return "unable to find any relevant information".
            """;

    private static final String PROMPT_TEMPLATE = """
            <query>
            """;

    private final ToolCallbackProvider toolProvider;

    public RetrievalAgent(
            @Value("${llmcr.agent.retrieval.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.retrieval.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            UserInteractionTool userInteractionTool, DatabaseTool databaseTool) {
        super(chatProviderName, chatModelName, modelClientFactory, null);

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