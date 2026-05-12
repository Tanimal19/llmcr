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
import com.llmcr.rag.retrieval.QueryContextRetriever;
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class ComputationAgent {

    public record ComputationAgentInput(
            List<CodeChange> codeChanges,
            String checklistItem) {
    }

    public record EvidenceItem(
            String file,
            String lines,
            String reason) {
    }

    public record ModelResponse(
            List<EvidenceItem> evidence,
            String finalAnswer,
            boolean needsAdditionalData,
            String dataQuery) {
    }

    private static final int MAX_ITERATION = 5;

    private static final String PROMPT_TEMPLATE = """
            You are an experienced code reviewer.
            Your task is to analyze the provided code change strictly based on the checklist item.
            You MUST only use information explicitly present in the provided code change and context.
            Do NOT speculate or assume missing implementation details.

            Your review process:
            1. Identify the code sections relevant to the checklist item.
            2. Extract explicit evidence from the code change.
            3. Analyze whether the evidence satisfies the checklist requirement.
            4. If required information is missing, STOP the analysis and request additional data instead of making assumptions.

            Rules:
            - Do not infer behavior from naming alone.
            - Do not assume omitted code behaves correctly.
            - Do not speculate about framework behavior unless explicitly shown.
            - If evidence is insufficient, set needsAdditionalData=true.
            - Do not repeat the same reasoning multiple times.
            - Keep reasoning concise and evidence-focused.

            Output format (JSON only):
            {
                "evidence": [
                    {
                        "file": "...",
                        "lines": "...",
                        "reason": "..."
                    },
                ],
                "analysis": "...",
                "finalAnswer": "...",
                "needsAdditionalData": false,
                "dataQuery": null
            }

            When information is insufficient:
            {
                "evidence": [...],
                "analysis": "The provided code does not contain enough information to verify the checklist item.",
                "finalAnswer": null,
                "needsAdditionalData": true,
                "dataQuery": "Please provide ..."
            }

            Checklist item:
            <checklist_description>

            Code changes:
            <code_changes>
            """;

    private final ChatClient chatClient;
    private final BeanOutputConverter<ModelResponse> outputConverter;
    private final RetrievalAgent retrievalAgent;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public ComputationAgent(SmallChatClient chatClient, RetrievalAgent retrievalAgent,
            QueryContextRetriever queryContextRetriever) {
        this.chatClient = chatClient.getChatClient();
        this.outputConverter = new BeanOutputConverter<>(ModelResponse.class);
        this.retrievalAgent = retrievalAgent;

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

        String first_message = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(PROMPT_TEMPLATE)
                .build()
                .render(Map.of(
                        "checklist_description", checklistDescription,
                        "code_changes", codeChangesText));

        int iteration = 0;
        ModelResponse response = null;
        String conversationId = "computation-" + System.currentTimeMillis();
        String retrievalResult = "";
        do {
            ChatClient.ChatClientRequestSpec requestSpec = chatClient
                    .prompt(first_message)
                    .advisors(new SimpleLoggerAdvisor(), memoryAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

            if (retrievalResult != null && !retrievalResult.isBlank()) {
                requestSpec = requestSpec.user(retrievalResult);
            }

            response = this.outputConverter.convert(requestSpec.call().content());

            if (response == null || !response.needsAdditionalData()) {
                break;
            }

            retrievalResult = retrievalAgent.execute(response.dataQuery());
            iteration++;
        } while (iteration <= MAX_ITERATION);

        return response == null ? "" : StringUtils.safeText(response.finalAnswer());
    }
}
