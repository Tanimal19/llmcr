package com.llmcr.feature.review.agent;

import com.llmcr.config.SystemConfig;
import com.llmcr.feature.review.tool.DatabaseTool;
import com.llmcr.feature.review.tool.MyToolCallingManager;
import com.llmcr.feature.review.tool.MyToolCallingManager.ToolCall;
import com.llmcr.infrastructure.agent.BaseAgent;
import com.llmcr.infrastructure.ai.ModelClientFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class RetrievalAgent extends BaseAgent<String, RetrievalAgent.RetrievalModelResponse, String> {

    public record RetrievalModelResponse(boolean hasToolCall, ToolCall toolCall) {
    }

    private String systemPrompt = """
            You are a retrieval planning agent.
            Your task is to determine the next best action based on the user's query and previous tool results.
            You do NOT answer the user's query directly.

            You may:
            1. Call a tool
            2. Finish the retrieval process

            You can only call one tool at a time.
            After each tool call, you will receive the tool result in the next iteration.
            Use tool calls to progressively refine or retrieve more specific information if needed.

            If you want to call a tool, output the following JSON:
            {
                "hasToolCall": true,
                "toolCall": {
                    "toolName": "the name of the tool you want to call, must be one of the available tools",
                    "arguments": {
                        "arg1": "value1",
                        "arg2": "value2"
                    }
                }
            }

            If you want to finish the retrieval process and provide a final answer, output the following JSON:
            {
                "hasToolCall": false,
                "toolCall": null
            }

            Return JSON only.

            Available tools:
            {tool_definitions}
            """;

    private static final String INITIAL_USER_MESSAGE_TEMPLATE = """
            User Query:
            <query>
            """;

    private static final String AGENT_NAME = "retrieval";
    private final MyToolCallingManager toolCallingManager;
    private List<String> toolResults;

    public RetrievalAgent(
            SystemConfig applicationProperties,
            ModelClientFactory modelClientFactory,
            DatabaseTool databaseTool) {
        super(
                AGENT_NAME,
                applicationProperties,
                modelClientFactory,
                new BeanOutputConverter<>(RetrievalModelResponse.class));
        ToolCallback[] toolCallbacks = ToolCallbacks.from(databaseTool);
        this.toolCallingManager = new MyToolCallingManager(toolCallbacks);

        StringBuilder toolDefBuilder = new StringBuilder();
        for (ToolCallback callback : toolCallbacks) {
            toolDefBuilder.append(callback.getToolDefinition()).append("\n----\n");
        }
        this.systemPrompt = this.systemPrompt.replace("{tool_definitions}", toolDefBuilder.toString());
    }

    @Override
    protected String getSystemMessage() {
        return systemPrompt;
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
                "You have called a tool: " +
                        response.toolCall().toString() +
                        "\nThe tool returned the following result:\n" +
                        toolResult +
                        "\nUse above information to determine your next action.");
    }

    @Override
    protected String buildFinalResponse(RetrievalModelResponse response) {
        return toolResults.isEmpty() ? "No information was retrieved." : toolResults.get(toolResults.size() - 1);
    }

    @Override
    protected String doExecute(String input) {
        this.toolResults = new ArrayList<>();
        return super.doExecute(input);
    }
}
