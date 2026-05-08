package com.llmcr.service.review.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import com.llmcr.agent.Agent;
import com.llmcr.agent.AgentInput;
import com.llmcr.client.ChatClientWrapper;
import com.llmcr.client.SmallChatClient;
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class ComputationAgent
        extends
        Agent<ComputationAgent.ComputationAgentInput, ComputationAgent.ComputationAgentOutput, ComputationAgent.ComputationAgentOutput> {

    public record ComputationAgentInput(
            List<CodeChange> codeChanges,
            String checklistItem,
            String previousAnalysis,
            String retrievalResult) implements AgentInput {

        @Override
        public Map<String, Object> getTemplateVariables() {
            List<CodeChange> safeCodeChanges = codeChanges == null ? List.of() : codeChanges;
            String codeChangesText = String.join("\n----\n", safeCodeChanges.stream()
                    .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                    .toList());

            String itemDescription = StringUtils.safeText(checklistItem);
            String safePreviousAnalysis = StringUtils.safeText(previousAnalysis);
            String safeRetrievalResult = StringUtils.safeText(retrievalResult);

            return Map.of(
                    "code_changes", codeChangesText,
                    "checklist_description", itemDescription,
                    "previous_analysis", safePreviousAnalysis,
                    "retrieval_result", safeRetrievalResult);
        }
    }

    public record ComputationAgentOutput(
            String answer,
            boolean needsAdditionalData,
            String dataQuery) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are now a experienced code reviewer.
            Your task is to analysis the checklist item based on provided information, and give a clear answer whether the checklist item is satisfied or not. If the checklist item is statisfied, answer with a detailed explanation why it is satisfied; if not satisfied, give a detailed explanation why it is not satisfied, and what is the potential risk.

            You will be given the code change, the checklist item to be checked, previous analysis for this checklist item (if any), and tool retrieval result (if any). You should make use of all provided information to give a comprehensive analysis follow the checklist item. Do not make any assumption beyond the provided information. If the given information is insufficient to answer, set needsAdditionalData=true and provide a dataQuery that specifies what additional information is needed.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            Here is the code change:
            <code_changes>

            Checklist item to be check: <checklist_description>

            Previous analysis for this checklist item:
            <previous_analysis>

            Previous tool retrieval result:
            <retrieval_result>
            """;

    private final SmallChatClient chatClient;

    public ComputationAgent(SmallChatClient chatClient) {
        super(
                null,
                null,
                1,
                null,
                false, false, true,
                SYSTEM_MESSAGE,
                "",
                USER_MESSAGE_TEMPLATE);
        this.chatClient = chatClient;
    }

    @Override
    protected Class<ComputationAgentOutput> modelOutputClass() {
        return ComputationAgentOutput.class;
    }

    @Override
    protected ChatClientWrapper chatClient() {
        return chatClient;
    }

    @Override
    protected ComputationAgentOutput constructAgentOutput(
            ResponseEntity<ChatResponse, ComputationAgentOutput> responseEntity) {
        return responseEntity.entity();
    }
}
