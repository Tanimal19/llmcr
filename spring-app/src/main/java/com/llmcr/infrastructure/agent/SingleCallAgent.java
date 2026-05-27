package com.llmcr.infrastructure.agent;

import com.llmcr.config.provider.AgentConfigProvider;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import org.springframework.ai.chat.messages.Message;

/** Base class for agents that only need to make a single call to the LLM. */
public abstract class SingleCallAgent<I, O> extends BaseAgent<I, O, O> {

  protected SingleCallAgent(
      AgentConfigProvider configProvider, ModelClientFactory modelClientFactory) {
    super(configProvider, modelClientFactory);
  }

  @Override
  protected boolean shouldTerminate(O response) {
    // only run one iteration, so always terminate after the first response
    return true;
  }

  @Override
  protected Message buildNextUserMessage(int iteration, O response) {
    // should never reach here
    return null;
  }

  @Override
  protected O buildAgentOutput(O modelResponse) {
    return modelResponse;
  }
}
