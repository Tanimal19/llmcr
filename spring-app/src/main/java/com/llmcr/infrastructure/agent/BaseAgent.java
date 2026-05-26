package com.llmcr.infrastructure.agent;

import com.llmcr.config.provider.AgentConfigProvider;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.util.StringUtils;
import com.llmcr.infrastructure.agent.logging.AgentContextHolder;
import com.llmcr.infrastructure.agent.logging.AgentLoggerAdvisor;
import com.llmcr.infrastructure.ai.ModelClientFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;

/**
 * Base class for agents that interact with LLMs through a chat interface. The
 * agent maintains conversation history and manages an iterative chat flow.
 */
public abstract class BaseAgent<I, R, O> implements Agent<I, O> {

    protected final ChatClient chatClient;
    protected final BeanOutputConverter<R> outputConverter;
    protected final String chatModelName;

    private static final int DEFAULT_MAX_RETRY = 3;
    private static final int DEFAULT_MAX_ITERATIONS = 5;
    private static final String FORMAT_INSTRUCTIONS_PLACEHOLDER = "format_instructions";

    private List<Message> conversationHistory;

    /**
     * If outputConverter is provided, the agent will use it to convert the raw
     * model response to type R. If outputConverter is null, the raw response will
     * be cast to R (which may cause a ClassCastException if R is not String).
     */
    protected BaseAgent(
            AgentConfigProvider configProvider,
            ModelClientFactory modelClientFactory) {
        this.chatClient = modelClientFactory.createChatClient(configProvider.getAgentChatModelConfig(getAgentName()));
        if (getOutputClass() != String.class) {
            this.outputConverter = new BeanOutputConverter<>(getOutputClass());
        } else {
            this.outputConverter = null;
        }
        this.chatModelName = configProvider.getAgentChatModelConfig(getAgentName()).name();
    }

    protected abstract String getAgentName();

    /**
     * Override this method if you want to use OutputConverter to automatically
     * convert the raw response to a structured object.
     */
    protected Class<R> getOutputClass() {
        return null;
    }

    /**
     * The system message that sets the behavior of the agent. This should not
     * contain any variables.
     */
    protected abstract String getSystemMessage();

    /**
     * The first user message sent to the agent. This usually include the user input
     * and instructions on how to use the input.
     * If {@link #FORMAT_INSTRUCTIONS_PLACEHOLDER} is included in the template, the
     * agent will replace it with the output format instructions based on the
     * outputConverter.
     */
    protected abstract String getInitialUserMessageTemplate();

    /**
     * This method should build a map of variables to be used in the initial user
     * message template.
     */
    protected abstract Map<String, Object> buildInputVariables(I input);

    protected abstract boolean shouldTerminate(R response);

    protected abstract Message buildNextUserMessage(int iteration, R response);

    protected abstract O buildAgentOutput(R modelResponse);

    protected int getMaxRetry() {
        return DEFAULT_MAX_RETRY;
    }

    protected int getMaxIterations() {
        return DEFAULT_MAX_ITERATIONS;
    }

    protected String buildInitialMessage(I input) {
        Map<String, Object> variables = new HashMap<>();
        variables.putAll(buildInputVariables(input));
        if (outputConverter != null) {
            variables.put(FORMAT_INSTRUCTIONS_PLACEHOLDER, outputConverter.getFormat());
        }

        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(getInitialUserMessageTemplate())
                .build()
                .render(variables);
    }

    protected ChatOptions buildChatOptions(I input) {
        return ChatOptions.builder().build();
    }

    @SuppressWarnings("unchecked")
    protected R convertRawResponse(String rawResponse) {
        if (rawResponse == null) {
            return null;
        }

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
                        .prompt("Fix this invalid JSON. Return ONLY valid JSON. DO NOT modify the content, only fix the format. The invalid JSON: "
                                + rawResponse)
                        .advisors(new AgentLoggerAdvisor("OutputFixAgent"));

                retryRequest.user(rawResponse);
                rawResponse = retryRequest.call().content();
            }
        }
        throw new APIServiceException(APIServiceException.ErrorCode.LLM_RESPONSE_CONVERSION_FAILED);
    }

    protected O doExecute(I input) {
        ChatOptions chatOptions = buildChatOptions(input);

        conversationHistory = new ArrayList<>();
        conversationHistory.add(new SystemMessage(getSystemMessage()));
        conversationHistory.add(new UserMessage(buildInitialMessage(input)));

        Prompt prompt;
        ChatResponse chatResponse;
        R modelResponse;

        int itreation = 0;
        do {
            prompt = Prompt.builder().messages(conversationHistory).chatOptions(chatOptions).build();
            ChatClientRequestSpec requestSpec = chatClient
                    .prompt(prompt)
                    .advisors(new AgentLoggerAdvisor(this.getClass().getSimpleName()));

            chatResponse = requestSpec.call().chatResponse();
            modelResponse = convertRawResponse(chatResponse.getResult().getOutput().getText());

            if (shouldTerminate(modelResponse)) {
                break;
            }

            // update conversation history
            List<Message> assistantMessages = chatResponse
                    .getResults()
                    .stream()
                    .map(g -> (Message) g.getOutput())
                    .toList();
            conversationHistory.addAll(assistantMessages);

            Message nextMessage = buildNextUserMessage(itreation + 1, modelResponse);
            if (nextMessage != null) {
                conversationHistory.add(nextMessage);
            }

            itreation++;
        } while (itreation <= getMaxIterations());

        return buildAgentOutput(modelResponse);
    }

    @Override
    public O execute(I input) {
        AgentContextHolder.beginContext(this.getClass().getSimpleName(), chatModelName);
        try {
            return doExecute(input);
        } finally {
            AgentContextHolder.endContext();
        }
    }
}
