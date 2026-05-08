package com.llmcr.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentExecuteEntry {
    public String agentName = "none";
    public String clientType = "none";
    public String conversationId = "none";
    public int totalIteration = 0;
    public Object input = "none";
    public Object output = "none";
    public String error = "none";
    public List<ModelCallEntry> modelCalls = new ArrayList<>();

    public static class ModelCallEntry {
        public Object request = "none";
        public Object response = "none";
    }
}
