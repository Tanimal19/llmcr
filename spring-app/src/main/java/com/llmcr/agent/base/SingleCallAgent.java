package com.llmcr.agent.base;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.converter.BeanOutputConverter;

import com.llmcr.service.ModelClientFactory;

/**
 * Base class for agents that only need to make a single call to the LLM.
 */
public abstract class SingleCallAgent<I, O> extends BaseAgent<I, O, O> {

    protected SingleCallAgent(String chatProviderName, String chatModelName,
            ModelClientFactory modelClientFactory, BeanOutputConverter<O> outputConverter) {
        super(chatProviderName, chatModelName, modelClientFactory, outputConverter);
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
    protected O buildFinalResponse(O modelResponse) {
        return modelResponse;
    }
}
