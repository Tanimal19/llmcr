package com.llmcr.agent;

import java.util.Map;

import com.llmcr.rag.RAGInput;

public interface AgentInput extends RAGInput {
    /**
     * Get the variables to be used for filling the agent's message template. The
     * keys of the returned map should correspond to the placeholders in the message
     * template.
     */
    Map<String, Object> getTemplateVariables();
}
