package com.llmcr.infrastructure.agent;

public interface Agent<I, O> {
  public O execute(I input);
}
