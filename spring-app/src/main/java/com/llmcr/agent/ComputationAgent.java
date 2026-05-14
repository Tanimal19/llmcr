package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.RecursiveAgent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class ComputationAgent
        extends
        RecursiveAgent<ComputationAgent.ComputationAgentInput, ComputationAgent.ModelResponse, ComputationAgent.ComputationAgentOutput> {

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
                new BeanOutputConverter<>(ModelResponse.class));
        this.retrievalAgent = retrievalAgent;
    }

    @Override
    protected String getPromptTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    protected Map<String, Object> getPromptVariables(ComputationAgentInput input) {
        String codeChangesText = String.join("\n----\n", input.codeChanges().stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());
        String checklistDescription = StringUtils.safeText(input == null ? null : input.checklistItem());

        return Map.of(
                "checklist_description", checklistDescription,
                "code_changes", codeChangesText);
    }

    @Override
    protected boolean shouldTerminate(ModelResponse response) {
        return !response.needsAdditionalData();
    }

    @Override
    protected String getNextMessage(ModelResponse response) {
        if (response.dataQuery() == null) {
            return "Your previous analysis indicated that additional data is needed, but the data query is missing. Please provide a data query to retrieve the necessary information.";
        }
        String retrievalResult = retrievalAgent.execute(response.dataQuery());
        return "You requested additional data with the following query:\n" + response.dataQuery()
                + "\nThe retrieved data is:\n" + retrievalResult;
    }

    @Override
    protected ComputationAgentOutput convertModelResponse(ModelResponse response) {
        return new ComputationAgentOutput(response.finalAnswer(), response.analysis(), response.evidence());
    }
}
