package com.llmcr.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;

public abstract class RecursiveAgent<I, R, O> extends Agent<I, R, O> {

    private final MessageChatMemoryAdvisor memoryAdvisor;

    protected RecursiveAgent(ChatClient chatClient, BeanOutputConverter<R> outputConverter) {
        super(chatClient, outputConverter);
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder().maxMessages(10).build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
    }

    protected abstract boolean shouldTerminate(R response);

    protected abstract String getNextMessage(R response);

    protected int maxIterations() {
        return 5;
    }

    @Override
    public O execute(I input) {

        String prompt = buildPrompt(input);
        String conversationId = Long.toString(System.currentTimeMillis());
        R modelResponse = null;

        int iteration = 0;
        do {
            ChatClientRequestSpec requestSpec = chatClient
                    .prompt(prompt)
                    .advisors(new SimpleLoggerAdvisor(), memoryAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

            String rawResponse = requestSpec.call().content();

            modelResponse = outputConverter.convert(rawResponse);

            if (shouldTerminate(modelResponse)) {
                break;
            }

            prompt = getNextMessage(modelResponse);
            iteration++;
        } while (iteration <= maxIterations());

        return convertModelResponse(modelResponse);
    }
}
