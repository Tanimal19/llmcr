package com.llmcr.agent;

public interface Agent<I, O> {
    public O execute(I input);
}
