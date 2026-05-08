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
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.lang.Nullable;

import com.llmcr.client.ChatClientWrapper;
import com.llmcr.rag.retrieval.QueryContextRetriever;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextScorePair;

/**
 * Abstract base class for implementing agents.
 * 
 * @param <I> The type of the agent input, which must implement AgentInput
 *            interface.
 * @param <T> The type of the intermediate model response.
 *            (This can be same as O if no intermediate response is needed)
 * @param <O> The type of the agent output.
 */
public abstract class Agent<I extends AgentInput, T, O> {

    protected abstract Class<T> modelOutputClass();

    protected abstract ChatClientWrapper chatClient();

    private final QueryContextRetriever QUERY_CONTEXT_RETRIEVER;
    private final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION;
    private final int MAX_ITERATION;

    protected List<Object> tools;

    private final boolean enableRag;
    private final boolean enableTools;
    private final boolean enableStructuredOutput;

    private final String TASK_INSTRUCTION;
    private final String FORMAT_INSTRUCTION;
    private final String CONTEXT_INPUT_TEMPLATE;
    private final String USER_INPUT_TEMPLATE;

    protected Agent(@Nullable QueryContextRetriever retriever,
            @Nullable ContextRetrievalConfiguration retrievalConfiguration,
            int maxIteration, @Nullable List<Object> tools,
            boolean enableRag, boolean enableTools, boolean enableStructuredOutput,
            String taskInstruction, String contextInputTemplate, String userInputTemplate) {

        this.QUERY_CONTEXT_RETRIEVER = retriever;
        this.RETRIEVAL_CONFIGURATION = retrievalConfiguration;
        this.MAX_ITERATION = maxIteration;
        this.tools = tools;

        this.enableRag = enableRag && RETRIEVAL_CONFIGURATION != null && QUERY_CONTEXT_RETRIEVER != null;
        this.enableTools = enableTools && tools != null && !tools.isEmpty();
        this.enableStructuredOutput = enableStructuredOutput;

        this.TASK_INSTRUCTION = taskInstruction;
        if (this.enableStructuredOutput) {
            this.FORMAT_INSTRUCTION = new BeanOutputConverter<>(modelOutputClass()).getFormat();
        } else {
            this.FORMAT_INSTRUCTION = "";
        }
        this.CONTEXT_INPUT_TEMPLATE = contextInputTemplate;
        this.USER_INPUT_TEMPLATE = userInputTemplate;

    }

    public O execute(I input, String conversationId) {
        AgentExecuteEntry executionEntry = new AgentExecuteEntry();
        executionEntry.agentName = getClass().getSimpleName();
        executionEntry.clientType = chatClient().getClass().getSimpleName();
        executionEntry.conversationId = (conversationId == null || conversationId.isBlank()) ? "none" : conversationId;
        executionEntry.input = input;

        List<Advisor> advisors = new ArrayList<>();
        Map<String, Object> advisorParams = new LinkedHashMap<>();

        // Set up advisors
        advisors.add(new SimpleLoggerAdvisor());

        if (enableStructuredOutput) {
            advisors.add(StructuredOutputValidationAdvisor.builder()
                    .outputType(modelOutputClass())
                    .maxRepeatAttempts(3)
                    .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 200)
                    .build());
        }

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory)
                .order(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                .build());
        advisorParams.put(ChatMemory.CONVERSATION_ID, conversationId);

        if (enableTools) {
            advisors.add(ToolCallAdvisor.builder()
                    .disableMemory()
                    .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 400)
                    .build());
        }

        // Build prompt
        Map<String, Object> templateVariables = input.getTemplateVariables();

        if (enableRag) {
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
            String nextMessage = initialMessageBuilder.toString();

            int iteration = 0;
            while (responseEntity == null || shouldContinue(responseEntity)) {

                if (iteration > 0) {
                    nextMessage = getNextMessage(responseEntity);
                }

                if (iteration >= MAX_ITERATION) {
                    nextMessage += "\nMaximum iteration reached. Please provide the final answer based on the current information.";
                }

                AgentExecuteEntry.ModelCallEntry modelCallEntry = new AgentExecuteEntry.ModelCallEntry();

                requestSpec = chatClient().getChatClient()
                        .prompt()
                        .user(nextMessage)
                        .advisors(advisors)
                        .advisors(spec -> spec.params(advisorParams));
                if (enableTools) {
                    requestSpec = requestSpec.tools(tools);
                }
                modelCallEntry.request = requestSpec;

                responseEntity = requestSpec.call().responseEntity(modelOutputClass());
                modelCallEntry.response = responseEntity.response();
                executionEntry.modelCalls.add(modelCallEntry);
                iteration++;
            }

            executionEntry.totalIteration = iteration;
            executionEntry.output = responseEntity.entity();
            return constructAgentOutput(responseEntity);
        } catch (Exception e) {
            executionEntry.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new RuntimeException("Failed to execute ChatClient: " + e.getMessage(), e);
        } finally {
            AgentExecutionLogger.write(executionEntry);
        }
    }

    private String buildPrompt(String template, Map<String, Object> variables) {
        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(template)
                .build()
                .render(variables);
    }

    /**
     * The default action is running a single iteration and return the output.
     */
    protected boolean shouldContinue(ResponseEntity<ChatResponse, T> responseEntity) {
        return false;
    }

    protected String getNextMessage(ResponseEntity<ChatResponse, T> responseEntity) {
        return "";
    }

    protected abstract O constructAgentOutput(ResponseEntity<ChatResponse, T> responseEntity);
}
