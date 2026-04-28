package com.llmcr.agent;

public interface Agent<I, O> {
    O execute(I input);
}
