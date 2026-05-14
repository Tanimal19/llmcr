package com.llmcr.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;

public abstract class SingleCallAgent<I, O> extends Agent<I, O, O> {

    protected SingleCallAgent(ChatClient chatClient, BeanOutputConverter<O> outputConverter) {
        super(chatClient, outputConverter);
    }

    @Override
    protected O convertModelResponse(O modelResponse) {
        return modelResponse;
    }
}
