package com.llmcr.feature.review.agent;

import com.llmcr.config.provider.AgentConfigProvider;
import com.llmcr.feature.review.CodeReviewReport.CodeChange;
import com.llmcr.feature.review.CodeReviewReport.ImplementationDetails;
import com.llmcr.feature.review.CodeReviewReport.IssueDraft;
import com.llmcr.infrastructure.agent.SingleCallAgent;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SingleReviewAgent
        extends SingleCallAgent<SingleReviewAgent.SingleReviewAgentInput, SingleReviewAgent.SingleReviewAgentOutput> {

    public record SingleReviewAgentInput(
            List<CodeChange> codeChanges) {
    }

    public record SingleReviewAgentOutput(
            String motivation,
            String suggestion,
            List<String> goodPoints,
            List<String> badPoints,
            List<ImplementationDetails> implementationDetails,
            List<IssueDraft> issues) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a senior Java code reviewer writing the final code review report.

            Your goal is to interpret the code change, identify possible issues, and summurize them into a structured report the author can use to improve the code change.

            ## Your task
            You will be given a code change, you need to produce:
            - motivation: why the change was made.
            - goodPoints: aspects of the change that are well done.
            - badPoints: aspects that could be improved but are not significant enough to be raised as issues.
            - suggestion: concrete, actionable suggestions for improvement, based on the bad points and issues.
            - implementationDetails: important implementation details reviewers should pay attention to (patterns used, non-obvious design decisions, etc.), grouped by file.
            - issues: the list of identified issues, reformatted for clarity and grouped by dimension.

            ## Rules
            - Be concise and specific. Avoid vague and general statements.
            - Do NOT make assumptions beyond the provided information.

            Think step by step internally before answering.

            ## Output Format
            Output only a JSON object:
            {
                "motivation": "why the change was made",
                "suggestion": "overall suggestion for the PR",
                "goodPoints": ["..."],
                "badPoints": ["..."],
                "implementationDetails": [
                    {"filename": "...", "details": "..."}
                ],
                "issues": [
                    {
                        "dimension": "Compatibility | Design | Security | Functionality | Performance | Maintainability | Readability",
                        "severity": "Critical | Major | Minor",
                        "location": "filename::line_number",
                        "title": "short issue title (max 10 words)",
                        "description": "what the issue is and why it is a problem",
                    },
                    ...
                ]
            }
            """;

    private static final String INITIAL_USER_MESSAGE_TEMPLATE = """
            **Code Changes (diff):**
            <code_changes>
            """;

    private static final String AGENT_NAME = "single-review";

    public SingleReviewAgent(AgentConfigProvider configProvider, ModelClientFactory modelClientFactory) {
        super(configProvider, modelClientFactory);
    }

    @Override
    protected String getAgentName() {
        return AGENT_NAME;
    }

    @Override
    protected Class<SingleReviewAgentOutput> getOutputClass() {
        return SingleReviewAgentOutput.class;
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
    protected Map<String, Object> buildInputVariables(SingleReviewAgentInput input) {
        StringBuilder codeChangesTextBuilder = new StringBuilder();
        int index = 1;
        for (CodeChange change : input.codeChanges()) {
            codeChangesTextBuilder
                    .append("[Code Change ")
                    .append(index)
                    .append("]\n")
                    .append(change.toString())
                    .append("\n\n");
            index++;
        }

        return Map.of("code_changes", codeChangesTextBuilder.toString());
    }
}
