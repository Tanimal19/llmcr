package com.llmcr.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;

import com.llmcr.client.ChatClientWrapper;

public abstract class Agent<I extends AgentInput, O> {

    public abstract ChatClientWrapper chatClient();

    public abstract String systemMessage();

    public abstract String userMessageTemplate();

    public abstract Class<O> outputClass();

    protected List<Advisor> advisors = new ArrayList<>();
    protected Map<String, Object> advisorParams = new LinkedHashMap<>();

    protected void preprocess(I input) {
        // default no-op, can be overridden by subclasses for input validation or
        // enrichment
    }

    protected void onSuccess(O output) {
        // default no-op, can be overridden by subclasses for output validation or
        // enrichment
    }

    protected void onError(Exception e) {
        // default no-op, can be overridden by subclasses for custom error handling
    }

    public O execute(I input) {
        try {
            preprocess(input);
            String userMessage = renderUserMessage(input);
            Prompt prompt = new Prompt()
                    .augmentSystemMessage(systemMessage())
                    .augmentUserMessage(userMessage);

            O response = chatClient().getChatClient().prompt(prompt)
                    .advisors(advisors)
                    .advisors(spec -> spec.params(advisorParams))
                    .call()
                    .entity(outputClass());
            onSuccess(response);
            return response;
        } catch (Exception e) {
            onError(e);
            throw new RuntimeException("Failed to execute ChatClient: " + e.getMessage(), e);
        }
    }

    protected String renderUserMessage(I input) {
        Map<String, Object> templateVariables = input.getTemplateVariables();
        PromptTemplate userMessagePromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(userMessageTemplate())
                .build();
        return userMessagePromptTemplate.render(templateVariables);
    }
}
