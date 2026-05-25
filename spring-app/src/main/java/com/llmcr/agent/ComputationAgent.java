package com.llmcr.agent;

import com.llmcr.agent.base.BaseAgent;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.service.review.CodeReviewService.CodeChange;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
public class ComputationAgent
    extends BaseAgent<
        ComputationAgent.ComputationAgentInput,
        ComputationAgent.ComputationModelResponse,
        ComputationAgent.ComputationAgentOutput
    > {

    public record ComputationAgentInput(List<CodeChange> codeChanges, String checklistItem) {}

    public record EvidenceItem(String file, String lines, String reason) {}

    public record ComputationModelResponse(
        List<EvidenceItem> evidence,
        String analysis,
        String finalAnswer,
        boolean needsAdditionalData,
        String dataQuery
    ) {}

    public record ComputationAgentOutput(String finalAnswer, String analysis, List<EvidenceItem> evidence) {
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Final Answer: ").append(finalAnswer).append("\n");
            sb.append("Analysis: ").append(analysis).append("\n");
            sb.append("Evidence:\n");
            if (evidence != null) {
                for (EvidenceItem item : evidence) {
                    sb
                        .append("- File: ")
                        .append(item.file())
                        .append(", Lines: ")
                        .append(item.lines())
                        .append(", Reason: ")
                        .append(item.reason())
                        .append("\n");
                }
            }
            return sb.toString();
        }
    }

    private static final String SYSTEM_PROMPT =
        """
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
        """;

    private static final String INITIAL_USER_MESSAGE_TEMPLATE =
        """
        Checklist item:
        <checklist_description>

        Code changes:
        <code_changes>
        """;
    private static final String AGENT_NAME = "computation";

    private final RetrievalAgent retrievalAgent;

    public ComputationAgent(
        ApplicationProperties applicationProperties,
        ModelClientFactory modelClientFactory,
        RetrievalAgent retrievalAgent
    ) {
        super(
            AGENT_NAME,
            applicationProperties,
            modelClientFactory,
            new BeanOutputConverter<>(ComputationModelResponse.class)
        );
        this.retrievalAgent = retrievalAgent;
    }

    @Override
    protected String getSystemMessage() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected String getInitialUserMessageTemplate() {
        return INITIAL_USER_MESSAGE_TEMPLATE;
    }

    @Override
    protected Map<String, Object> buildInputVariables(ComputationAgentInput input) {
        String codeChangesText = String.join(
            "\n----\n",
            input
                .codeChanges()
                .stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList()
        );
        String checklistDescription = input.checklistItem();

        return Map.of("checklist_description", checklistDescription, "code_changes", codeChangesText);
    }

    @Override
    protected boolean shouldTerminate(ComputationModelResponse response) {
        return !response.needsAdditionalData();
    }

    @Override
    protected Message buildNextUserMessage(int iteration, ComputationModelResponse response) {
        if (iteration >= getMaxIterations() - 1) {
            return new UserMessage(
                "THIS IS YOUR FINAL ITERATION. You can't request more data. Please provide your best possible analysis based on the available information, but clearly state the limitations of your analysis due to missing information."
            );
        }

        if (response.dataQuery() == null) {
            return new UserMessage(
                "Your previous analysis indicated that additional data is needed, but the data query is missing. Please provide a data query to retrieve the necessary information."
            );
        }

        String retrievalResult = retrievalAgent.execute(response.dataQuery());
        return new UserMessage(
            "You requested additional data with the following query: " +
            response.dataQuery() +
            "\nThe retrieval result is: " +
            retrievalResult +
            "\nPlease use this information to continue your analysis."
        );
    }

    @Override
    protected ComputationAgentOutput buildFinalResponse(ComputationModelResponse response) {
        return new ComputationAgentOutput(response.finalAnswer(), response.analysis(), response.evidence());
    }
}
