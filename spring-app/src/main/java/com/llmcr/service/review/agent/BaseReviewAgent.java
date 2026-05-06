package com.llmcr.service.review.agent;

import com.llmcr.agent.Agent;
import com.llmcr.agent.AgentCallEntry;
import com.llmcr.agent.AgentInput;
import com.llmcr.service.review.trace.LoggingAdvisor;
import com.llmcr.service.review.trace.ReviewTraceCollector;
import com.llmcr.service.review.trace.ReviewTraceContext;

public abstract class BaseReviewAgent<I extends AgentInput, O> extends Agent<I, O> {

    private ReviewTraceCollector traceCollector;
    private AgentCallEntry entry;

    protected BaseReviewAgent() {
        super.advisors.add(new LoggingAdvisor());
    }

    protected void preprocess(I input) {
        // reset entry for each call
        traceCollector = ReviewTraceContext.current();
        entry = new AgentCallEntry();
        super.advisorParams.put(LoggingAdvisor.AGENT_CALL_ENTRY, entry);
        this.entry.agentName = this.getClass().getSimpleName();
        this.entry.clientType = this.chatClient().getClass().getSimpleName();
        this.entry.input = input.getTemplateVariables();
    }

    protected void onSuccess(O output) {
        this.entry.output = output;
        traceCollector.addAgentCall(entry);
    }

    protected void onError(Exception e) {
        this.entry.error = e.getMessage();
        traceCollector.addAgentCall(entry);
    }
}