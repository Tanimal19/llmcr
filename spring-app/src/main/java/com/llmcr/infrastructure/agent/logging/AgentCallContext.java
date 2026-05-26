package com.llmcr.infrastructure.agent.logging;

import java.util.ArrayList;
import java.util.List;

public class AgentCallContext {

    public String agentName;
    public String modelName;
    public Long startedAt;
    public Long endedAt;
    public Long durationMs;
    public Object input;
    public Object output;
    public final List<AgentCallContext> iterationHistory = new ArrayList<>();

    public void addIteration(AgentCallContext iteration) {
        if (iteration != null) {
            this.iterationHistory.add(iteration);
        }
    }

    public void finish() {
        this.endedAt = System.currentTimeMillis();
        if (this.startedAt != null) {
            this.durationMs = this.endedAt - this.startedAt;
        }
    }
}
