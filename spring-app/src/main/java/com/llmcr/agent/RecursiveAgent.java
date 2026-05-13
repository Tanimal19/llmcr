package com.llmcr.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;

public abstract class RecursiveAgent<I, R, O> extends Agent<I, O> {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final BeanOutputConverter<R> outputConverter;

    protected RecursiveAgent(ChatClient chatClient, BeanOutputConverter<R> outputConverter) {
        this.chatClient = chatClient;
        this.outputConverter = outputConverter;
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder().maxMessages(10).build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
    }

    protected abstract String getFirstMessage(I input);

    protected abstract boolean shouldTerminate(R response);

    protected abstract String getNextMessage(R response);

    protected abstract O formatFinalAnswer(R response);

    protected int maxIterations() {
        return 5;
    }

    @Override
    public O execute(I input) {
        log.debug("[{}] execute called", getClass().getSimpleName());

        String firstMessage = getFirstMessage(input);
        String conversationId = Long.toString(System.currentTimeMillis());
        R response = null;

        int iteration = 0;
        do {
            ChatClient.ChatClientRequestSpec requestSpec = chatClient
                    .prompt(firstMessage)
                    .advisors(new SimpleLoggerAdvisor(), memoryAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

            if (retrievalResult != null && !retrievalResult.isBlank()) {
                requestSpec = requestSpec.user(retrievalResult);
            }

            response = outputConverter.convert(requestSpec.call().content());

            if (response == null || shouldTerminate(response)) {
                break;
            }

            retrievalResult = fetchAdditionalData(getDataQuery(response));
            iteration++;
        } while (iteration <= maxIterations());

        if (response == null) {
            return "";
        }

        String result = formatFinalAnswer(response);
        log.debug("[{}] execute completed", getClass().getSimpleName());
        return result;
    }
}
