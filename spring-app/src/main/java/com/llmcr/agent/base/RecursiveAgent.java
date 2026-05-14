package com.llmcr.agent.base;

import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;

import com.llmcr.agent.logging.AgentLoggerAdvisor;
import com.llmcr.service.ModelClientFactory;

public abstract class RecursiveAgent<I, R, O> extends Agent<I, R, O> {

    private final MessageChatMemoryAdvisor memoryAdvisor;

    protected RecursiveAgent(String chatProviderName, String chatModelName,
            ModelClientFactory modelClientFactory, BeanOutputConverter<R> outputConverter) {
        super(chatProviderName, chatModelName, modelClientFactory, outputConverter);
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder().maxMessages(6).build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
    }

    protected abstract boolean shouldTerminate(R response);

    protected abstract String getNextMessage(R response);

    protected abstract String getFinalMessage();

    protected int maxIterations() {
        return 5;
    }

    @Override
    public O doExecute(I input) {
        String system_prompt = buildPrompt(input);
        String conversationId = Long.toString(System.currentTimeMillis());
        R modelResponse = null;

        int iteration = 0;
        do {
            ChatClientRequestSpec requestSpec = chatClient
                    .prompt()
                    .system(system_prompt)
                    .advisors(new AgentLoggerAdvisor(this.getClass().getSimpleName()), memoryAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

            if (modelResponse != null) {
                String nextMessage = getNextMessage(modelResponse);
                if (iteration == maxIterations() - 1) {
                    nextMessage = nextMessage + "\n" + getFinalMessage();
                }
                requestSpec = requestSpec.user(getNextMessage(modelResponse));
            }

            String rawResponse = requestSpec.call().content();
            modelResponse = convertRawResponse(rawResponse);

            if (shouldTerminate(modelResponse)) {
                break;
            }

            iteration++;
        } while (iteration <= maxIterations());

        return convertModelResponse(modelResponse);
    }
}
