package com.llmcr.agent.base;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

import com.llmcr.service.ModelClientFactory;

/**
 * Base class for agents that only need to make a single call to the LLM.
 */
public abstract class SingleCallAgent<I, O> extends Agent<I, O, O> {

    protected SingleCallAgent(String chatProviderName, String chatModelName,
            ModelClientFactory modelClientFactory, BeanOutputConverter<O> outputConverter) {
        super(chatProviderName, chatModelName, modelClientFactory, outputConverter);
    }

    @Override
    protected boolean shouldTerminate(ChatResponse chatResponse, O response) {
        // only run one iteration, so always terminate after the first response
        return true;
    }

    @Override
    protected List<Message> buildNextMessages(Prompt prompt, ChatResponse chatResponse, O response) {
        // should never reach here
        return List.of();
    }

    @Override
    protected Message buildFinalMessage() {
        // should never reach here
        return null;
    }

    @Override
    protected O convertModelResponse(O modelResponse) {
        return modelResponse;
    }
}
