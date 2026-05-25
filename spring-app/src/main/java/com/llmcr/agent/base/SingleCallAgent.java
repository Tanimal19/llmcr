package com.llmcr.agent.base;

import com.llmcr.config.ApplicationProperties;
import com.llmcr.service.ModelClientFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Base class for agents that only need to make a single call to the LLM.
 */
public abstract class SingleCallAgent<I, O> extends BaseAgent<I, O, O> {

    protected SingleCallAgent(
        String agentName,
        ApplicationProperties applicationProperties,
        ModelClientFactory modelClientFactory,
        BeanOutputConverter<O> outputConverter
    ) {
        super(agentName, applicationProperties, modelClientFactory, outputConverter);
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
