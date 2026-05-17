package com.llmcr.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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
import com.llmcr.agent.logging.AgentLoggerAdvisor;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.UserInteractionTool;

@Component
public class PromptRetrievalAgent extends Agent<String, String, String> {

    private static final int MAX_ITERATIONS = 5;

    private static final String SYSTEM_PROMPT = """
            You are an assistant that help retrieve relevant information based on user queries.
            You should analyze the user's query and identify the relevant tools to call based on the provided tool definitions.
            Do not infer content from document name alone. Do not make up information that is not provided by the tools.

            Example interaction:
            User query="What are the details of X?"
            Thought="I need to find documents relevant to X, then get the details of those documents."
            Call Tool=similaritySearch("X")
            Observation="Document A, Document B are relevant to X."
            Thought="I will get the details of Document A first."
            Call Tool=getDocumentById("Document A id")
            Observation="The details of Document A are ..."
            Thought="Now I have the details of Document A, I can answer the user's query."
            Final Answer="The details of X are ..."

            Avalailable tools:
            <tool_definitions>
            """;

    private static final String PROMPT_TEMPLATE = """
            <query>
            """;

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final ToolCallback[] toolCallbacks;
    private final ChatOptions chatOptions;

    public PromptRetrievalAgent(
            @Value("${llmcr.agent.retrieval.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.retrieval.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            DatabaseTool databaseTool, UserInteractionTool userInteractionTool) {
        super(chatProviderName, chatModelName, modelClientFactory, null);

        toolCallbacks = ToolCallbacks.from(databaseTool, userInteractionTool);

        chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Override
    protected String getPromptTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    protected Map<String, Object> getPromptVariables(String input) {
        StringBuilder toolDefinitionsBuilder = new StringBuilder();
        for (ToolCallback toolCallback : toolCallbacks) {
            ToolDefinition def = toolCallback.getToolDefinition();
            toolDefinitionsBuilder.append(def.toString()).append("\n");
        }
        return Map.of("tool_definitions", toolDefinitionsBuilder.toString(), "query", input);
    }

    @Override
    protected String convertModelResponse(String rawResponse) {
        return rawResponse.trim();
    }

    @Override
    public String doExecute(String input) {

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(buildPrompt(input)));
        ChatResponse chatReponse = null;
        String modelResponse = null;

        int iteration = 0;
        do {
            Prompt prompt = Prompt.builder().messages(messages).chatOptions(chatOptions).build();
            ChatClientRequestSpec requestSpec = chatClient
                    .prompt(prompt)
                    .advisors(new AgentLoggerAdvisor(this.getClass().getSimpleName()));

            chatReponse = requestSpec.call().chatResponse();
            modelResponse = convertRawResponse(chatReponse.getResult().getOutput().getText());

            if (chatReponse.hasToolCalls()) {
                // call tools
                ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, chatReponse);
                messages.addAll(toolResult.conversationHistory());
            } else {
                break;
            }

            iteration++;
        } while (iteration <= MAX_ITERATIONS);

        return convertModelResponse(modelResponse);
    }
}