package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import com.llmcr.client.SmallChatClient;
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class ComputationAgent {

    public record ComputationAgentInput(
            List<CodeChange> codeChanges,
            String checklistItem) {
    }

    public record ModelResponse(
            String innerThought,
            String finalAnswer,
            boolean needsAdditionalData,
            String dataQuery) {
    }

    private static final int MAX_ITERATION = 5;

    private static final String PROMPT_TEMPLATE = """
            You are now a experienced code reviewer.
            Your task is to perform code review based on the given code change and checklist item. You should give a clear and comprehensive analysis that follows the checklist item.

            You will be given a code change and a checklist item to check. You should analyze the code change based on the checklist item and provide your analysis in the final answer.

            Do not make any assumption beyond the provided information. If the given information is insufficient to answer, set needsAdditionalData=true and provide a dataQuery that specifies what additional information is needed. When providing dataQuery, make sure it is specific and provide enough details. Do not provide vague or irrelevant dataQuery.

            When you have enough information to answer, set needsAdditionalData=false and provide the answer. The answer should not be only 'yes' or 'no', but should include detailed reasons and your step-by-step reasoning.

            Below is the code change:
            <code_changes>

            Checklist item to be analysis: <checklist_description>

            Below is a few examples of how to answer:
            <examples>

            Think step by step internally before answering.
            """;

    private final ChatClient chatClient;
    private final RetrievalAgent retrievalAgent;
    private final BeanOutputConverter<ModelResponse> outputConverter;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public ComputationAgent(SmallChatClient chatClient, RetrievalAgent retrievalAgent) {
        this.chatClient = chatClient.getChatClient();
        this.retrievalAgent = retrievalAgent;
        this.outputConverter = new BeanOutputConverter<>(ModelResponse.class);

        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
    }

    public String execute(ComputationAgentInput input) {
        List<CodeChange> safeCodeChanges = input == null || input.codeChanges() == null ? List.of()
                : input.codeChanges();
        String codeChangesText = String.join("\n----\n", safeCodeChanges.stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());
        String checklistDescription = StringUtils.safeText(input == null ? null : input.checklistItem());

        String system_message = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(PROMPT_TEMPLATE)
                .build()
                .render(Map.of(
                        "code_changes", codeChangesText,
                        "checklist_description", checklistDescription));
        system_message = system_message + "\n\n" + outputConverter.getFormat();

        int iteration = 0;
        ModelResponse response = null;
        String conversationId = "computation-" + System.currentTimeMillis();
        String retrievalResult = "";
        do {
            ChatClient.ChatClientRequestSpec requestSpec = chatClient
                    .prompt()
                    .system(system_message)
                    .advisors(new SimpleLoggerAdvisor(), memoryAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

            if (retrievalResult != null && !retrievalResult.isBlank()) {
                requestSpec = requestSpec.user(retrievalResult);
            }

            response = requestSpec
                    .call()
                    .entity(ModelResponse.class);

            if (response == null || !response.needsAdditionalData()) {
                break;
            }

            retrievalResult = retrievalAgent.execute(response.dataQuery());
            iteration++;
        } while (iteration <= MAX_ITERATION);

        return response == null ? "" : StringUtils.safeText(response.finalAnswer());
    }
}
