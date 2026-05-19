package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.Agent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.tool.InteractionTool.Interactable;
import com.llmcr.util.GitDiffParser.CodeChange;

@Component
public class ComputationAgent
        extends
        Agent<ComputationAgent.ComputationAgentInput, ComputationAgent.ComputationModelResponse, ComputationAgent.ComputationAgentOutput>
        implements Interactable {

    public record ComputationAgentInput(
            List<CodeChange> codeChanges,
            String checklistItem) {
    }

    public record EvidenceItem(
            String file,
            String lines,
            String reason) {
    }

    public record ComputationModelResponse(
            List<EvidenceItem> evidence,
            String analysis,
            String finalAnswer,
            boolean needsAdditionalData,
            String dataQuery) {
    }

    public record ComputationAgentOutput(
            String finalAnswer,
            String analysis,
            List<EvidenceItem> evidence) {

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Final Answer: ").append(finalAnswer).append("\n");
            sb.append("Analysis: ").append(analysis).append("\n");
            sb.append("Evidence:\n");
            if (evidence != null) {
                for (EvidenceItem item : evidence) {
                    sb.append("- File: ").append(item.file()).append(", Lines: ").append(item.lines())
                            .append(", Reason: ").append(item.reason()).append("\n");
                }
            }
            return sb.toString();
        }
    }

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
            5. If you can't get the required additional data after multiple iteration, provide the best possible analysis based on the available information, but clearly state the limitations of your analysis.

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
                        "file": "Example.java",
                        "lines": "10-20",
                        "reason": "Because ..."
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

            You should output JSON only, and strictly follow the output format. Do NOT include any explanations or comments outside the JSON structure. Every string should be wrapped in double quotes.

            Checklist item:
            <checklist_description>

            Code changes:
            <code_changes>
            """;

    private final RetrievalAgent retrievalAgent;

    public ComputationAgent(
            @Value("${llmcr.agent.computation.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.computation.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            RetrievalAgent retrievalAgent) {
        super(chatProviderName, chatModelName, modelClientFactory,
                new BeanOutputConverter<>(ComputationModelResponse.class));
        this.retrievalAgent = retrievalAgent;
    }

    @Override
    protected String buildInitialMessageTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    protected Map<String, Object> buildInputVariables(ComputationAgentInput input) {
        String codeChangesText = String.join("\n----\n", input.codeChanges().stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());
        String checklistDescription = input.checklistItem();

        return Map.of(
                "checklist_description", checklistDescription,
                "code_changes", codeChangesText);
    }

    @Override
    protected boolean shouldTerminate(ChatResponse chatResponse, ComputationModelResponse response) {
        return !response.needsAdditionalData();
    }

    @Override
    protected List<Message> buildNextMessages(Prompt prompt, ChatResponse chatResponse,
            ComputationModelResponse response) {
        if (response.dataQuery() == null) {
            return List.of(new UserMessage(
                    "Your previous analysis indicated that additional data is needed, but the data query is missing. Please provide a data query to retrieve the necessary information."));
        }
        String retrievalResult = retrievalAgent.execute(
                new RetrievalAgent.RetrievalAgentInput(response.dataQuery(), this));
        return List.of(new UserMessage(
                "The agent requested additional data with the following query: " + response.dataQuery()
                        + "\nThe retrieval result is: " + retrievalResult
                        + "\nPlease use this information to continue your analysis."));
    }

    @Override
    protected Message buildFinalMessage() {
        return new UserMessage(
                "THIS IS YOUR FINAL ITERATION. You can't request more data. Please provide your best possible analysis based on the available information, but clearly state the limitations of your analysis due to missing information.");
    }

    @Override
    protected ComputationAgentOutput convertModelResponse(ComputationModelResponse response) {
        return new ComputationAgentOutput(response.finalAnswer(), response.analysis(), response.evidence());
    }

    @Override
    public String askFollowUp(String question) {
        List<Message> currentConversation = getConversationHistoryCopy();
        currentConversation.add(new UserMessage(
                """
                        Here is a follow-up question respond by the tool. Please answer this question to clarify the needed addtional data.
                        Question: <question>
                        """
                        .replace("<question>", question)));

        Prompt prompt = Prompt.builder().messages(currentConversation).build();
        ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();
        return chatResponse.getResults().stream()
                .map(g -> g.getOutput().getText())
                .findFirst()
                .orElse("(tool error: no response from model)");
    }
}
