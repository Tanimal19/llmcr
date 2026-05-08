package com.llmcr.service.review.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import com.llmcr.agent.Agent;
import com.llmcr.agent.AgentInput;
import com.llmcr.client.ChatClientWrapper;
import com.llmcr.client.LargeChatClient;
import com.llmcr.rag.retrieval.QueryContextRetriever;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.rag.retrieval.select.AdaptiveKStrategy;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.util.GitDiffParser.CodeChange;

@Component
public class InterpretationAgent
        extends Agent<InterpretationAgentInput, InterpretationAgentOutput, InterpretationAgentOutput> {

    public record InterpretationAgentInput(List<CodeChange> codeChanges) implements AgentInput {
        @Override
        public List<String> buildQueries() {
            return codeChanges.stream()
                    .map(change -> change.filePath() + "\n" + change.diffContent())
                    .toList();
        }

        @Override
        public Map<String, Object> getTemplateVariables() {
            String codeChangesText = String.join("\n----\n", codeChanges.stream()
                    .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                    .toList());

            return Map.of("code_changes", codeChangesText);
        }
    }

    public record InterpretationAgentOutput(String changeDescription, String changeMotivation) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are now a software engineer experienced at Java and Spring Framework.
            Your task is to interpret the code change by describing what was changed, and the movitation of the changes. For the motivation, you should consider why the original code was insufficient.
            You will be given a code change and a list of project context.
            Do not make assumptions beyond the provided information. Focus on analyzing the code change based on the given context.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            Below is the code change you need to interpret:
            <code_changes>
            """;

    private static final String CONTEXT_MESSAGE_TEMPLATE = """
            Below is a list of project context:
            <context>
            """;

    private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
            10, new AdaptiveKStrategy(), "project-context", false);

    private final LargeChatClient chatClient;

    public InterpretationAgent(LargeChatClient chatClient, QueryContextRetriever queryContextRetriever) {
        super(
                queryContextRetriever,
                RETRIEVAL_CONFIGURATION,
                1,
                null,
                true, false, true,
                SYSTEM_MESSAGE,
                CONTEXT_MESSAGE_TEMPLATE,
                USER_MESSAGE_TEMPLATE);
        this.chatClient = chatClient;
    }

    @Override
    protected Class<InterpretationAgentOutput> modelOutputClass() {
        return InterpretationAgentOutput.class;
    }

    @Override
    protected ChatClientWrapper chatClient() {
        return chatClient;
    }

    @Override
    protected InterpretationAgentOutput constructAgentOutput(
            ResponseEntity<ChatResponse, InterpretationAgentOutput> responseEntity) {
        return responseEntity.entity();
    }
}
