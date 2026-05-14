package com.llmcr.agent.base;

import org.springframework.ai.converter.BeanOutputConverter;

import com.llmcr.service.ModelClientFactory;

public abstract class SingleCallAgent<I, O> extends Agent<I, O, O> {

    protected SingleCallAgent(String chatProviderName, String chatModelName,
            ModelClientFactory modelClientFactory, BeanOutputConverter<O> outputConverter) {
        super(chatProviderName, chatModelName, modelClientFactory, outputConverter);
    }

    @Override
    protected O convertModelResponse(O modelResponse) {
        return modelResponse;
    }
}
