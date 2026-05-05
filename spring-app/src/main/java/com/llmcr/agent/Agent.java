package com.llmcr.agent;

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

    /**
     * Build the parameters for the advisors based on the input. This method will be
     * called before executing the agent, and the returned parameters will be passed
     * to the advisors during the prompt call.
     */
    public abstract Map<String, Object> buildAdvisorParams(I input);

    protected List<Advisor> advisors;
    private Class<O> outputClass;

    protected Agent(Class<O> outputClass) {
        this.outputClass = outputClass;
    }

    public O execute(I input) {
        // fill the user message template with variables from input
        PromptTemplate userMessagePromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(userMessageTemplate())
                .build();
        String userMessage = userMessagePromptTemplate.render(input.getTemplateVariables());

        // construct the prompt with system message and user message
        Prompt prompt = new Prompt()
                .augmentSystemMessage(systemMessage())
                .augmentUserMessage(userMessage);

        Map<String, Object> advisorParams = buildAdvisorParams(input);

        try {
            O response = chatClient().getChatClient().prompt(prompt)
                    .advisors(advisors)
                    .advisors(spec -> spec.params(advisorParams))
                    .call()
                    .entity(outputClass);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute ChatClient: " + e.getMessage(), e);
        }
    }
}
