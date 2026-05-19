package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.llmcr.agent.base.SingleCallAgent;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.service.rag.QueryContextRetriever;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.select.AdaptiveKStrategy;
import com.llmcr.util.GitDiffParser.CodeChange;

@Component
public class InterpretationAgent extends
        SingleCallAgent<InterpretationAgent.InterpretationAgentInput, InterpretationAgent.InterpretationAgentOutput> {

    public record InterpretationAgentInput(List<CodeChange> codeChanges) {
    }

    public record InterpretationAgentOutput(String changeDescription, String changeMotivation) {
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

    private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
            10, new AdaptiveKStrategy(), "project-context", false);

    private final QueryContextRetriever queryContextRetriever;

    public InterpretationAgent(
            @Value("${llmcr.agent.interpretation.chat.provider}") String chatProviderName,
            @Value("${llmcr.agent.interpretation.chat.model}") String chatModelName,
            ModelClientFactory modelClientFactory,
            QueryContextRetriever queryContextRetriever) {
        super(chatProviderName, chatModelName, modelClientFactory,
                new BeanOutputConverter<>(InterpretationAgentOutput.class));
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
        String codeChangesText = String.join("\n----\n", input.codeChanges().stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());
        String contextText = retrieveContext(input);
        return Map.of("code_changes", codeChangesText, "context", contextText);
    }

    private String retrieveContext(InterpretationAgentInput input) {
        List<String> queries = input.codeChanges().stream()
                .map(change -> change.filePath() + "\n" + change.diffContent())
                .toList();
        List<ContextScorePair> retrievedContexts = queryContextRetriever
                .retrieve(new ContextRetrievalRequest(queries, RETRIEVAL_CONFIGURATION));
        return String.join("\n---\n", retrievedContexts.stream()
                .map(pair -> pair.context().getContent())
                .toList());
    }
}
