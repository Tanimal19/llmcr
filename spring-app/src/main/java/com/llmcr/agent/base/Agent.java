package com.llmcr.agent.base;

public interface Agent<I, O> {
    public O execute(I input);
}
