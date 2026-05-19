package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.Agent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.InteractionTool;

@Component
public class RetrievalAgent extends Agent<RetrievalAgent.RetrievalAgentInput, String, String> {

    public record RetrievalAgentInput(String query, InteractionTool.Interactable caller) {
    }

    private static final String PROMPT_TEMPLATE = """
            You are an assistant that help retrieve relevant information based on user queries.

            Rules:
            - Do not infer content from document name alone.
            - Do not make up information that is not provided by the tools.

            Avalailable tools:
            <tool_definitions>

            User Query:
            <query>
            """;

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final ToolCallback[] toolCallbacks;

    public RetrievalAgent(
            @Value("${llmcr.agent.retrieval.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.retrieval.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            DatabaseTool databaseTool, InteractionTool interactionTool) {
        super(chatProviderName, chatModelName, modelClientFactory, null);
        toolCallbacks = ToolCallbacks.from(databaseTool, interactionTool);
    }

    @Override
    protected ChatOptions buildChatOptions(RetrievalAgentInput input) {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .toolContext(Map.of("caller", input.caller()))
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Override
    protected String buildInitialMessageTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    protected Map<String, Object> buildInputVariables(RetrievalAgentInput input) {
        StringBuilder toolDefinitionsBuilder = new StringBuilder();
        for (ToolCallback toolCallback : toolCallbacks) {
            ToolDefinition def = toolCallback.getToolDefinition();
            toolDefinitionsBuilder.append("name: ").append(def.name()).append("\n");
            toolDefinitionsBuilder.append("description: ").append(def.description()).append("\n");
            toolDefinitionsBuilder.append("input schema: ").append(def.inputSchema()).append("\n");
            toolDefinitionsBuilder.append("-----\n");
        }
        return Map.of("<tool_definitions>", toolDefinitionsBuilder.toString(), "query", input.query());
    }

    @Override
    protected boolean shouldTerminate(ChatResponse chatResponse, String response) {
        return !chatResponse.hasToolCalls();
    }

    @Override
    protected List<Message> buildNextMessages(Prompt prompt, ChatResponse chatResponse, String response) {
        ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
        return toolResult.conversationHistory();
    }

    @Override
    protected Message buildFinalMessage() {
        return new UserMessage(
                "THIS IS YOUR FINAL ITERATION. You can't call more tools. Please provide your best possible answer based on the available information, but clearly state the limitations of your answer due to missing information.");
    }

    @Override
    protected String convertModelResponse(String rawResponse) {
        return rawResponse.trim();
    }
}