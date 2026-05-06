package com.llmcr.agent;

public class AgentExecuteEntry {
    public String agentName = "none";
    public String clientType = "none";
    public String conversationId = "none";
    public int totalIteration = 0;
    public Object input = "none";
    public Object output = "none";
    public String error = "none";

    public class ModelCallEntry {
        public Object request = "none";
        public Object response = "none";
    }
}
