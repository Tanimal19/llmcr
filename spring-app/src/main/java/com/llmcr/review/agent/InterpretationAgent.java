package com.llmcr.review.agent;

import com.llmcr.agent.SingleCallAgent;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.model.ModelClientFactory;
import com.llmcr.rag.QueryContextRetriever;
import com.llmcr.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.rag.ContextScorePair;
import com.llmcr.rag.select.AdaptiveKStrategy;
import com.llmcr.review.CodeReviewReport.CodeChange;
import com.llmcr.review.CodeReviewReport.InterpretationContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
public class InterpretationAgent
        extends
        SingleCallAgent<InterpretationAgent.InterpretationAgentInput, InterpretationContent> {

    public record InterpretationAgentInput(List<CodeChange> codeChanges) {
    }

    private static final String SYSTEM_PROMPT = """
            You are now a software engineer experienced at Java and Spring Framework.

            Your task is to interpret the code change by describing what was changed, and the movitation of the changes. For the motivation, you should consider why the original code was insufficient and what problem the change is trying to solve.

            You will be given code changes and project context retrieved based on the code changes. The project context may include information such as related code snippets, documentation, discussions, etc. You should make use of the project context when interpreting the code change. Do not make assumptions beyond the provided information. Focus on analyzing the code change based on the given context.
            """;

    private static final String INITIAL_USER_MESSAGE_TEMPLATE = """
            Below is a list of project context:
            <context>

            Below is the code change you need to interpret:
            <code_changes>

            <format_instructions>
            """;

    private static final String AGENT_NAME = "interpretation";
    private final ContextRetrievalConfiguration retrievalConfiguration;
    private final QueryContextRetriever queryContextRetriever;

    public InterpretationAgent(
            ApplicationProperties applicationProperties,
            ModelClientFactory modelClientFactory,
            QueryContextRetriever queryContextRetriever) {
        super(
                AGENT_NAME,
                applicationProperties,
                modelClientFactory,
                new BeanOutputConverter<>(InterpretationContent.class));
        this.retrievalConfiguration = new ContextRetrievalConfiguration(
                10,
                new AdaptiveKStrategy(),
                applicationProperties.getAgents().get(AGENT_NAME).getCollection(),
                false);
        this.queryContextRetriever = queryContextRetriever;
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
    protected Map<String, Object> buildInputVariables(InterpretationAgentInput input) {
        String codeChangesText = String.join(
                "\n----\n",
                input
                        .codeChanges()
                        .stream()
                        .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                        .toList());
        String contextText = retrieveContext(input);
        return Map.of("code_changes", codeChangesText, "context", contextText);
    }

    private String retrieveContext(InterpretationAgentInput input) {
        List<String> queries = new ArrayList<>();
        for (CodeChange change : input.codeChanges()) {
            queries.add(change.filePath());
            queries.add(change.diffContent());
        }

        List<ContextScorePair> retrievedContexts = queryContextRetriever.retrieve(
                new ContextRetrievalRequest(queries, retrievalConfiguration));
        return String.join("\n---\n", retrievedContexts.stream().map(pair -> pair.context().getContent()).toList());
    }
}
