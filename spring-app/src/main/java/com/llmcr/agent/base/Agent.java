package com.llmcr.agent.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import com.llmcr.agent.logging.AgentContextHolder;
import com.llmcr.agent.logging.AgentLoggerAdvisor;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.util.StringUtils;

/**
 * Base class for agents that interact with LLMs through a chat interface. The
 * agent maintains conversation history and manages an iterative chat flow.
 */
public abstract class Agent<I, R, O> {

    protected final String chatProviderName;
    protected final String chatModelName;
    protected final ChatClient chatClient;
    protected final BeanOutputConverter<R> outputConverter;
    private static final int DEFAULT_MAX_RETRY = 3;
    private static final int DEFAULT_MAX_ITERATIONS = 5;

    private List<Message> conversationHistory;

    /**
     * If outputConverter is provided, the agent will use it to convert the raw
     * model response to type R. If outputConverter is null, the raw response will
     * be cast to R (which may cause a ClassCastException if R is not String).
     */
    protected Agent(String chatProviderName, String chatModelName,
            ModelClientFactory modelClientFactory, BeanOutputConverter<R> outputConverter) {
        this.chatProviderName = chatProviderName;
        this.chatModelName = chatModelName;
        this.chatClient = modelClientFactory.createChatClient(chatProviderName, chatModelName);
        this.outputConverter = outputConverter;
    }

    protected int getMaxRetry() {
        return DEFAULT_MAX_RETRY;
    }

    protected int getMaxIterations() {
        return DEFAULT_MAX_ITERATIONS;
    }

    protected List<Message> getConversationHistoryCopy() {
        return new ArrayList<>(conversationHistory);
    }

    protected abstract String buildInitialMessageTemplate();

    protected abstract Map<String, Object> buildInputVariables(I input);

    protected String buildInitialMessage(I input) {
        Map<String, Object> variables = new HashMap<>();
        variables.putAll(buildInputVariables(input));
        variables.put("format_instructions", outputConverter != null ? outputConverter.getFormat() : "");

        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(buildInitialMessageTemplate())
                .build()
                .render(variables);
    }

    protected ChatOptions buildChatOptions(I input) {
        return ChatOptions.builder().build();
    }

    protected Prompt buildPrompt(ChatOptions chatOptions) {
        return Prompt.builder().messages(conversationHistory).chatOptions(chatOptions).build();
    }

    @SuppressWarnings("unchecked")
    protected R convertRawResponse(String rawResponse) {
        int attempt = 0;
        while (true) {
            try {
                String cleaned = StringUtils.cleanMarkdownCodeBlocks(rawResponse);
                return outputConverter != null ? outputConverter.convert(cleaned) : (R) cleaned;
            } catch (Exception e) {
                attempt++;
                if (attempt >= getMaxRetry()) {
                    break;
                }

                ChatClientRequestSpec retryRequest = chatClient
                        .prompt("Fix this invalid JSON. Return ONLY valid JSON.")
                        .advisors(new AgentLoggerAdvisor("OutputFixAgent"));

                retryRequest.user(rawResponse);
                rawResponse = retryRequest.call().content();
            }
        }
        throw new RuntimeException("Failed to convert model response after " + getMaxRetry() + " attempts");
    }

    protected abstract boolean shouldTerminate(ChatResponse chatResponse, R response);

    protected abstract List<Message> buildNextMessages(Prompt prompt, ChatResponse chatResponse, R response);

    protected abstract Message buildFinalMessage();

    protected abstract O convertModelResponse(R modelResponse);

    protected O doExecute(I input) {
        ChatOptions chatOptions = buildChatOptions(input);

        conversationHistory = new ArrayList<>();
        conversationHistory.add(new UserMessage(buildInitialMessage(input)));

        ChatResponse chatResponse;
        R modelResponse;

        int itreation = 0;
        do {
            Prompt prompt = buildPrompt(chatOptions);
            ChatClientRequestSpec requestSpec = chatClient
                    .prompt(prompt)
                    .advisors(new AgentLoggerAdvisor(this.getClass().getSimpleName()));

            chatResponse = requestSpec.call().chatResponse();
            modelResponse = convertRawResponse(chatResponse.getResult().getOutput().getText());

            if (shouldTerminate(chatResponse, modelResponse)) {
                break;
            }

            // update conversation history
            List<Message> assistantMessages = chatResponse
                    .getResults()
                    .stream()
                    .map(g -> (Message) g.getOutput())
                    .toList();
            conversationHistory.addAll(assistantMessages);

            List<Message> nextMessages = buildNextMessages(prompt, chatResponse, modelResponse);
            conversationHistory.addAll(nextMessages);

            itreation++;
        } while (itreation <= getMaxIterations());

        return convertModelResponse(modelResponse);
    }

    public O execute(I input) {
        AgentContextHolder.beginContext(this.getClass().getSimpleName(), chatModelName);
        try {
            return doExecute(input);
        } finally {
            AgentContextHolder.endContext();
        }
    }
}
