package com.llmcr.service.review.agent;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.ai.chat.client.ChatClient;

import com.llmcr.model.advisor.RAGAdvisor;
import com.llmcr.service.rag.ContextRetriever.RetrievalConfiguration;
import com.llmcr.service.rag.select.AdaptiveKStrategy;

public class Agent<I, O> {

    private static final Object[] NO_TOOLS = new Object[0];

    private final AgentInvokeStrategy<I, O> strategy;
    private final RAGAdvisor ragAdvisor;
    private final AgentStepLogger agentStepLogger;

    public Agent(AgentInvokeStrategy<I, O> strategy, RAGAdvisor ragAdvisor, AgentStepLogger agentStepLogger) {
        this.strategy = strategy;
        this.ragAdvisor = ragAdvisor;
        this.agentStepLogger = agentStepLogger;
    }

    public O execute(I input) {
        String userMessage = strategy.parseInput(input);
        Object[] tools = strategy.tools(input);
        return executeInternal(input, userMessage, () -> invoke(strategy.systemPrompt(), userMessage, input, tools));
    }

    private O executeInternal(I input, String userMessage, Supplier<ChatClient.CallResponseSpec> callSupplier) {
        String agentName = strategy.getClass().getSimpleName();
        try {
            ChatClient.CallResponseSpec response = callSupplier.get();
            O output = strategy.parseOutput(response);
            agentStepLogger.logSuccess(agentName, input, userMessage, output);
            return output;
        } catch (RuntimeException ex) {
            agentStepLogger.logFailure(agentName, input, userMessage, ex);
            throw ex;
        }
    }

    public final ChatClient.CallResponseSpec invoke(String userMessage, I input) {
        return invoke(strategy.systemPrompt(), userMessage, input, strategy.tools(input));
    }

    public final ChatClient.CallResponseSpec invoke(String systemPrompt, String userMessage, I input,
            Object... tools) {
        Object[] resolvedTools = tools == null ? NO_TOOLS : tools;
        RetrievalConfiguration retrievalConfiguration = retrievalConfiguration();
        List<String> retrievalQueries = strategy.ragQueries(input, userMessage);

        var requestSpec = strategy.chatClient()
                .prompt()
                .advisors(spec -> {
                    spec.advisors(ragAdvisor);
                    if (retrievalConfiguration != null) {
                        spec.param(RAGAdvisor.RETRIEVAL_CONFIGURATION_PARAM, retrievalConfiguration);
                        if (retrievalQueries != null && !retrievalQueries.isEmpty()) {
                            spec.param(RAGAdvisor.QUERY_LIST_PARAM, retrievalQueries);
                        }
                    }
                })
                .system(systemPrompt)
                .user(userMessage);

        if (resolvedTools.length == 0) {
            return requestSpec.call();
        }

        return requestSpec.tools(resolvedTools).call();
    }

    private RetrievalConfiguration retrievalConfiguration() {
        Integer topK = strategy.ragTopK();
        String collectionName = strategy.ragCollectionName();
        if (topK == null || collectionName == null || collectionName.isBlank()) {
            return null;
        }

        return new RetrievalConfiguration(topK, collectionName, false, new AdaptiveKStrategy());
    }
}
