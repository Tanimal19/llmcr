package com.llmcr.service.review.trace;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;

/**
 * This advisor is responsible for collecting the raw prompt and raw output of
 * each agent call and returning them in response.context()
 */
public class LoggingAdvisor implements BaseAdvisor {

    public static final String AGENT_CALL_ENTRY = "agentCallEntry";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, @Nullable AdvisorChain chain) {
        Map<String, Object> context = new HashMap<>(request.context());
        if (context.containsKey(AGENT_CALL_ENTRY)) {
            AgentCallEntry entry = (AgentCallEntry) context.get(AGENT_CALL_ENTRY);
            entry.rawPrompt = request.prompt().getContents();
        }
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, @Nullable AdvisorChain chain) {
        Map<String, Object> context = new HashMap<>(response.context());
        if (context.containsKey(AGENT_CALL_ENTRY)) {
            AgentCallEntry entry = (AgentCallEntry) context.get(AGENT_CALL_ENTRY);
            entry.rawOutput = response.chatResponse().toString();
        }

        return response;
    }
}