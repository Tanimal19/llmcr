package com.llmcr.agent;

import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.BaseAgent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.MyToolCallingManager;
import com.llmcr.tool.MyToolCallingManager.ToolCall;

@Component
public class RetrievalAgent extends BaseAgent<String, RetrievalAgent.RetrievalModelResponse, String> {

    public record RetrievalModelResponse(
            String finalAnswer,
            boolean hasToolCall,
            ToolCall toolCall) {
    }

    private static final String SYSTEM_PROMPT = """
            You are an assistant that help retrieve information relevant to the user's query.
            You will be given a set of tools that you can call, your task is to call the appropriate tools with appropriate arguments to retrieve relevant information to answer the user's query.
            You could only call one tool each time, after you call the tool, you will get the tool execution result as input for next iteration, you can then decide to call another tool or provide the final answer to the user.

            Output format (JSON only):
            {
                "finalAnswer": "Your final answer to the user's query",
                "hasToolCall": false,
                "toolCall": null
            }

            When you want to call a tool:
            {
                "finalAnswer": null,
                "hasToolCall": true,
                "toolCall": {
                    "toolName": "the name of the tool you want to call, must be one of the available tools",
                    "arguments": {
                        "arg1": "value1",
                        "arg2": "value2"
                    }
                }
            }

            You should output JSON only, and strictly follow the output format. Do NOT include any explanations or comments outside the JSON structure. Every string should be wrapped in double quotes.
            """;

    private String INITIAL_USER_MESSAGE_TEMPLATE = """
            Available tools:
            {tool_definitions}

            User Query:
            <query>
            """;

    private final MyToolCallingManager toolCallingManager;

    public RetrievalAgent(
            @Value("${llmcr.agent.retrieval.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.retrieval.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            DatabaseTool databaseTool) {
        super(chatProviderName, chatModelName, modelClientFactory,
                new BeanOutputConverter<>(RetrievalModelResponse.class));

        ToolCallback[] toolCallbacks = ToolCallbacks.from(databaseTool);
        this.toolCallingManager = new MyToolCallingManager(toolCallbacks);

        StringBuilder toolDefBuilder = new StringBuilder();
        for (ToolCallback callback : toolCallbacks) {
            toolDefBuilder.append(callback.getToolDefinition()).append("\n----\n");
        }
        this.INITIAL_USER_MESSAGE_TEMPLATE = this.INITIAL_USER_MESSAGE_TEMPLATE.replace("{tool_definitions}",
                toolDefBuilder.toString());
    }

    @Override
    protected String getSystemMessage() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected String getInitialUserMessageTemplate() {
        return INITIAL_USER_MESSAGE_TEMPLATE;
    }

    @Override
    protected Map<String, Object> buildInputVariables(String input) {
        return Map.of("query", input);
    }

    @Override
    protected boolean shouldTerminate(RetrievalModelResponse response) {
        return !response.hasToolCall();
    }

    @Override
    protected Message buildNextUserMessage(int iteration, RetrievalModelResponse response) {
        String toolResult = toolCallingManager.executeToolCall(response.toolCall());

        return new UserMessage(
                "You have called a tool: " + response.toolCall().toString()
                        + "\nThe tool returned the following result:\n"
                        + toolResult);
    }

    @Override
    protected String buildFinalResponse(RetrievalModelResponse response) {
        return response.finalAnswer();
    }
}