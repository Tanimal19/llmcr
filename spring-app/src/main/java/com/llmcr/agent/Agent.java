package com.llmcr.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.lang.Nullable;

import com.llmcr.client.ChatClientWrapper;
import com.llmcr.service.rag.retrieval.QueryContextRetriever;
import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextScorePair;

/**
 * Abstract base class for implementing agents.
 * 
 * @param <I> The type of the agent input, which must implement AgentInput
 *            interface.
 * @param <T> The type of the intermediate model response. (This can be same as
 *            O
 *            if no intermediate response is needed)
 * @param <O> The type of the agent output.
 */
public abstract class Agent<I extends AgentInput, T, O> {

    protected abstract Class<T> modelOutputClass();

    protected abstract ChatClientWrapper chatClient();

    private static final ToolCallingManager TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

    private final QueryContextRetriever QUERY_CONTEXT_RETRIEVER;
    private final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION;
    private final int MAX_ITERATION;

    private final String TASK_INSTRUCTION;
    private final String FORMAT_INSTRUCTION;
    private final String CONTEXT_INPUT_TEMPLATE;
    private final String USER_INPUT_TEMPLATE;

    private List<Object> tools;

    protected boolean useRag() {
        return true;
    }

    protected boolean useTools() {
        return true;
    }

    Agent(QueryContextRetriever retriever,
            @Nullable ContextRetrievalConfiguration retrievalConfiguration,
            int maxIteration,
            String taskInstruction,
            String contextInputTemplate,
            String userInputTemplate,
            @Nullable List<Object> tools) {

        this.QUERY_CONTEXT_RETRIEVER = retriever;
        this.RETRIEVAL_CONFIGURATION = retrievalConfiguration;
        this.MAX_ITERATION = maxIteration;

        this.TASK_INSTRUCTION = taskInstruction;
        this.FORMAT_INSTRUCTION = new BeanOutputConverter<>(modelOutputClass()).getFormat();
        this.CONTEXT_INPUT_TEMPLATE = contextInputTemplate;
        this.USER_INPUT_TEMPLATE = userInputTemplate;

        this.tools = tools;
    }

    public O execute(I input, String conversationId) {
        List<Advisor> advisors = new ArrayList<>();
        Map<String, Object> advisorParams = new LinkedHashMap<>();
        boolean ragEnabled = useRag() && RETRIEVAL_CONFIGURATION != null && QUERY_CONTEXT_RETRIEVER != null;
        boolean toolsEnabled = useTools() && tools != null && !tools.isEmpty();

        // Set up advisors
        advisors.add(new SimpleLoggerAdvisor());

        advisors.add(StructuredOutputValidationAdvisor.builder()
                .outputType(modelOutputClass())
                .maxRepeatAttempts(3)
                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 200)
                .build());

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory)
                .order(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                .build());
        advisorParams.put(ChatMemory.CONVERSATION_ID, conversationId);

        if (toolsEnabled) {
            advisors.add(ToolCallAdvisor.builder()
                    .toolCallingManager(TOOL_CALLING_MANAGER)
                    .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 400)
                    .build());
        }

        // Build prompt
        Map<String, Object> templateVariables = input.getTemplateVariables();

        if (ragEnabled) {
            assert CONTEXT_INPUT_TEMPLATE.contains("<context>")
                    : "Context input template must contain <context> placeholder for retrieved context";

            List<String> queries = input.buildQueries();
            List<ContextScorePair> retrievedContexts = QUERY_CONTEXT_RETRIEVER
                    .retrieve(new ContextRetrievalRequest(queries, RETRIEVAL_CONFIGURATION));

            StringBuilder contextBuilder = new StringBuilder();
            for (ContextScorePair pair : retrievedContexts) {
                contextBuilder.append(pair.context().getContent()).append("\n---\n");
            }
            String renderedContext = contextBuilder.toString();
            templateVariables.put("context", renderedContext);
        }

        String contextMessage = buildPrompt(CONTEXT_INPUT_TEMPLATE, templateVariables);
        String userInputMessage = buildPrompt(USER_INPUT_TEMPLATE, templateVariables);

        StringBuilder initialMessageBuilder = new StringBuilder();
        initialMessageBuilder.append(TASK_INSTRUCTION).append("\n\n")
                .append(contextMessage).append("\n\n")
                .append(userInputMessage).append("\n\n")
                .append(FORMAT_INSTRUCTION);

        try {
            ChatClientRequestSpec requestSpec;
            ResponseEntity<ChatResponse, T> responseEntity = null;
            String messageForNextTurn = initialMessageBuilder.toString();

            int iteration = 0;
            while (responseEntity == null || shouldContinue(responseEntity)) {

                if (iteration > 0) {
                    messageForNextTurn = getNextMessage(responseEntity);
                }

                if (iteration >= MAX_ITERATION) {
                    messageForNextTurn += "\nMaximum iteration reached. Please provide the final answer based on the current information.";
                }

                requestSpec = chatClient().getChatClient()
                        .prompt()
                        .user(messageForNextTurn)
                        .advisors(advisors)
                        .advisors(spec -> spec.params(advisorParams));
                if (toolsEnabled) {
                    requestSpec = requestSpec.tools(tools);
                }

                responseEntity = requestSpec.call().responseEntity(modelOutputClass());
                iteration++;
            }

            return extractOutput(responseEntity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute ChatClient: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String template, Map<String, Object> variables) {
        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(template)
                .build()
                .render(variables);
    }

    protected abstract boolean shouldContinue(ResponseEntity<ChatResponse, T> responseEntity);

    protected abstract String getNextMessage(ResponseEntity<ChatResponse, T> responseEntity);

    protected abstract O extractOutput(ResponseEntity<ChatResponse, T> responseEntity);
}
