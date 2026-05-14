package com.llmcr.agent;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(RetrievalAgent.class);

    private static final String PROMPT_TEMPLATE = """
            You are a retrieval assistant.
            Your task is to answer the user's data query by calling tools. You should try to find relevant information from the tool results to answer the query.
            If all tools tried and you still cannot find relevant information to answer the query, it's okay to say "I couldn't find relevant information to answer the query" rather than making up an answer.
            When generating the final answer, keep the answer concise and directly relevant to the query.

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

        logger.info("RetrievalAgent initialized with tools: {}", Arrays.toString(toolProvider.getToolCallbacks()));
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
        return requestSpec.toolCallbacks(toolProvider);
    }

}