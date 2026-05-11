package com.llmcr.agent;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.augment.AugmentedToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import com.llmcr.client.SmallChatClient;
import com.llmcr.tool.DatabaseTool;
import com.llmcr.tool.UserInteractionTool;
import com.llmcr.util.StringUtils;

@Component
public class RetrievalAgent {

    private static final Logger log = LoggerFactory.getLogger(RetrievalAgent.class);

    private static final String SYSTEM_MESSAGE = """
            You are a retrieval assistant.
            Your task is to answer the user's data query by calling tools. You should try to find relevant information from the tool results to answer the query.
            If all tools tried and you still cannot find relevant information to answer the query, it's okay to say "I couldn't find relevant information to answer the query" rather than making up an answer.
            When generating the final answer, keep the answer concise and directly relevant to the query.
            """;

    private final ChatClient chatClient;
    private final AugmentedToolCallbackProvider<ToolReasoning> augmentedToolProvider;

    public record ToolReasoning(
            @ToolParam(description = "Your step-by-step reasoning for why you're calling this tool and what you expect", required = true) String innerThought,

            @ToolParam(description = "Confidence level (low, medium, high) in this tool choice", required = false) String confidence) {
    };

    public RetrievalAgent(SmallChatClient chatClient,
            UserInteractionTool userInteractionTool, DatabaseTool databaseTool) {

        var delegateProvider = MethodToolCallbackProvider.builder()
                .toolObjects(List.of(databaseTool, userInteractionTool).toArray())
                .build();
        this.augmentedToolProvider = AugmentedToolCallbackProvider.<ToolReasoning>builder()
                .delegate(delegateProvider)
                .argumentType(ToolReasoning.class)
                .argumentConsumer(event -> {
                    ToolReasoning reasoning = event.arguments();
                    String toolName = event.toolDefinition().name();

                    log.info("=== Tool Call Reasoning ===");
                    log.info("Tool: {}", toolName);
                    log.info("Inner Thought: {}", reasoning.innerThought());
                    log.info("Confidence: {}",
                            reasoning.confidence() != null ? reasoning.confidence() : "not specified");
                })
                .build();

        this.chatClient = chatClient.getChatClient();
    }

    public String execute(String dataQuery) {
        String safeQuery = StringUtils.safeText(dataQuery);
        if (safeQuery.isBlank()) {
            return "Query is empty.";
        }

        ChatClientRequestSpec requestSpec = chatClient
                .prompt()
                .system(SYSTEM_MESSAGE)
                .user(safeQuery)
                .advisors(
                        new SimpleLoggerAdvisor(),
                        ToolCallAdvisor.builder()
                                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 400)
                                .build())
                .toolCallbacks(augmentedToolProvider);

        return requestSpec.call().content();
    }

}