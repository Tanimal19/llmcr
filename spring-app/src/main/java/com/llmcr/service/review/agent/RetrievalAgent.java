package com.llmcr.service.review.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.stereotype.Component;

import com.llmcr.agent.AgentInput;
import com.llmcr.client.ChatClientWrapper;
import com.llmcr.client.SmallChatClient;
import com.llmcr.service.rag.ContextAugmentAdvisor;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.UserInteractionTool;
import com.llmcr.util.StringUtils;

@Component
public class RetrievalAgent
        extends BaseReviewAgent<RetrievalAgent.RetrievalAgentInput, RetrievalAgent.RetrievalAgentOutput> {

    public record RetrievalAgentInput(
            String dataQuery,
            List<String> toolResponses) implements AgentInput {

        @Override
        public Map<String, Object> getTemplateVariables() {
            String toolResponsesText = toolResponses == null || toolResponses.isEmpty()
                    ? ""
                    : String.join("\n----\n", toolResponses);

            return Map.of(
                    "data_query", StringUtils.safeText(dataQuery),
                    "tool_responses", toolResponsesText);
        }
    }

    public record ToolRequest(String toolName, Map<String, Object> arguments, String purpose) {
    }

    public record RetrievalAgentOutput(
            List<ToolRequest> toolRequests,
            boolean satisfied,
            String refinedQuery) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are now a retrieval planning model.
            Your task is to decide what tool calls should be made to satisfy the data query.
            If existing tool responses already satisfy the query, set satisfied=true and return an empty toolRequests list. If not satisfied, set satisfied=false and propose the minimum set of concrete tool requests.
            If no tool call can satisfy the query, ask user for feedback on whether the query is too ambiguous or too difficult to answer.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            Data query:
            <data_query>

            Previous tool responses:
            <tool_responses>
            """;

    private final SmallChatClient chatClient;
    private final UserInteractionTool userInteractionTool;
    private final DatabaseTool databaseTool;

    public RetrievalAgent(SmallChatClient chatClient,
            UserInteractionTool userInteractionTool,
            DatabaseTool databaseTool) {
        this.chatClient = chatClient;
        this.userInteractionTool = userInteractionTool;
        this.databaseTool = databaseTool;
    }

    @Override
    public ChatClientWrapper chatClient() {
        return chatClient;
    }

    @Override
    public String systemMessage() {
        return SYSTEM_MESSAGE;
    }

    @Override
    public Class<RetrievalAgentOutput> outputClass() {
        return RetrievalAgentOutput.class;
    }

    @Override
    protected void preprocess(RetrievalAgentInput input) {
        super.preprocess(input);
        super.advisorParams.put(ContextAugmentAdvisor.RAG_INPUT, input);
    }

    @Override
    protected ChatClientRequestSpec enrichRequestSpec(ChatClientRequestSpec requestSpec) {
        return requestSpec.tools(userInteractionTool, databaseTool);
    }

    @Override
    public String userMessageTemplate() {
        return USER_MESSAGE_TEMPLATE;
    }

}