package com.llmcr.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import com.llmcr.client.LargeChatClient;
import com.llmcr.rag.retrieval.QueryContextRetriever;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextScorePair;
import com.llmcr.rag.retrieval.select.AdaptiveKStrategy;
import com.llmcr.util.GitDiffParser.CodeChange;

@Component
public class InterpretationAgent {

    public record InterpretationAgentInput(List<CodeChange> codeChanges) {
    }

    public record InterpretationAgentOutput(String changeDescription, String changeMotivation) {
    }

    private static final String PROMPT_TEMPLATE = """
            You are now a software engineer experienced at Java and Spring Framework.

            Your task is to interpret the code change by describing what was changed, and the movitation of the changes. For the motivation, you should consider why the original code was insufficient and what problem the change is trying to solve.

            You will be given code changes and project context retrieved based on the code changes. The project context may include information such as related code snippets, documentation, discussions, etc. You should make use of the project context when interpreting the code change. Do not make assumptions beyond the provided information. Focus on analyzing the code change based on the given context.

            Below is the code change you need to interpret:
            <code_changes>

            Below is a list of project context:
            <context>

            Think step by step before answering.
            """;

    private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
            10, new AdaptiveKStrategy(), "project-context", false);

    private final ChatClient chatClient;
    private final QueryContextRetriever queryContextRetriever;
    private final BeanOutputConverter<InterpretationAgentOutput> outputConverter;

    public InterpretationAgent(LargeChatClient chatClient, QueryContextRetriever queryContextRetriever) {
        this.chatClient = chatClient.getChatClient();
        this.queryContextRetriever = queryContextRetriever;
        this.outputConverter = new BeanOutputConverter<>(InterpretationAgentOutput.class);
    }

    public InterpretationAgentOutput execute(InterpretationAgentInput input) {

        String codeChangesText = String.join("\n----\n", input.codeChanges().stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());
        String contextText = retrieveContext(input);

        String prompt = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(PROMPT_TEMPLATE)
                .build()
                .render(Map.of("code_changes", codeChangesText, "context", contextText));
        prompt = prompt + "\n\n" + outputConverter.getFormat();

        ResponseEntity<ChatResponse, InterpretationAgentOutput> response = chatClient
                .prompt(prompt)
                .advisors(new SimpleLoggerAdvisor())
                .call()
                .responseEntity(InterpretationAgentOutput.class);

        return response.entity();
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
