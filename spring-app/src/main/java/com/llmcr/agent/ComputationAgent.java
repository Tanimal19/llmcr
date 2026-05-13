package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import com.llmcr.client.SmallChatClient;
import com.llmcr.rag.retrieval.QueryContextRetriever;
import com.llmcr.util.GitDiffParser.CodeChange;
import com.llmcr.util.StringUtils;

@Component
public class ComputationAgent
        extends RecursiveAgent<ComputationAgent.ComputationAgentInput, ComputationAgent.ModelResponse> {

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

    private final RetrievalAgent retrievalAgent;

    public ComputationAgent(SmallChatClient chatClient, RetrievalAgent retrievalAgent,
            QueryContextRetriever queryContextRetriever) {
        super(chatClient.getChatClient(), new BeanOutputConverter<>(ModelResponse.class));
        this.retrievalAgent = retrievalAgent;
    }

    @Override
    protected String buildFirstMessage(ComputationAgentInput input) {
        List<CodeChange> safeCodeChanges = input == null || input.codeChanges() == null ? List.of()
                : input.codeChanges();
        String codeChangesText = String.join("\n----\n", safeCodeChanges.stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());
        String checklistDescription = StringUtils.safeText(input == null ? null : input.checklistItem());

        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(PROMPT_TEMPLATE)
                .build()
                .render(Map.of(
                        "checklist_description", checklistDescription,
                        "code_changes", codeChangesText));
    }

    @Override
    protected boolean shouldRequestMoreData(ModelResponse response) {
        return response.needsAdditionalData();
    }

    @Override
    protected String getDataQuery(ModelResponse response) {
        return response.dataQuery();
    }

    @Override
    protected String fetchAdditionalData(String query) {
        return retrievalAgent.execute(query);
    }

    @Override
    protected String formatFinalAnswer(ModelResponse response) {
        return response.finalAnswer() + "\nAnalysis:\n" + response.analysis() + "\nEvidence:\n"
                + response.evidence().stream()
                        .map(e -> String.format("- file: %s, lines: %s, reason: %s",
                                e.file(), e.lines(), e.reason()))
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
    }
}
