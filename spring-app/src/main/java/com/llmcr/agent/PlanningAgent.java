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

import com.llmcr.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.client.LargeChatClient;
import com.llmcr.rag.retrieval.QueryContextRetriever;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.rag.retrieval.QueryContextRetriever.ContextScorePair;
import com.llmcr.rag.retrieval.select.AdaptiveKStrategy;
import com.llmcr.util.GitDiffParser.CodeChange;

@Component
public class PlanningAgent {

    public record PlanningAgentInput(
            List<CodeChange> codeChanges,
            InterpretationAgentOutput codeInterpretation,
            String codeAnalysis) {
    }

    public record PlanningAgentOutput(String innerThought, List<String> checklistItems) {
    }

    private static final String PROMPT_TEMPLATE = """
            You are now a software engineer experienced at Java and Spring Framework.
            Your task is to generate a checklist for code review.
            Here are aspects you should consider, including but not limited to:
            - Compatibility: does the change fit existing code and intended usage scenarios?
            - Design: is the change well-structured and aligned with best practices?
            - Security: does it introduce vulnerabilities?
            - Functionality: does it work as intended?
            - Performance: does it introduce inefficiencies?
            - Maintainability: is it easy to understand and modify later?
            - Readability: is it clear and understandable?

            You will be given code changes, a change interpretation, static analysis outputs, and review guidelines.
            Do not make assumptions beyond the provided information.
            Think step by step internally before answering.

            Below is the code change:
            <code_changes>

            Below is the change description:
            <change_description>

            Below is the code analysis:
            <code_analysis>

            Below is a list of review guideline to be used as reference when creating the checklist:
            <context>

            Create 5 to 8 checklist items.
            Each item should be a concise question that focuses on one specific aspect to verify.
            Avoid vague or open-ended items.
            """;

    private static final ContextRetrievalConfiguration RETRIEVAL_CONFIGURATION = new ContextRetrievalConfiguration(
            10, new AdaptiveKStrategy(), "guidelines", false);

    private final ChatClient chatClient;
    private final QueryContextRetriever queryContextRetriever;
    private final BeanOutputConverter<PlanningAgentOutput> outputConverter;

    public PlanningAgent(LargeChatClient chatClient, QueryContextRetriever queryContextRetriever) {
        this.chatClient = chatClient.getChatClient();
        this.queryContextRetriever = queryContextRetriever;
        this.outputConverter = new BeanOutputConverter<>(PlanningAgentOutput.class);
    }

    public PlanningAgentOutput execute(PlanningAgentInput input) {
        String codeChangesText = String.join("\n----\n", input.codeChanges().stream()
                .map(change -> "File: " + change.filePath() + "\nDiff: " + change.diffContent())
                .toList());

        InterpretationAgentOutput interpretation = input.codeInterpretation();
        String descriptionText = interpretation.changeDescription() + "\n" + interpretation.changeMotivation();
        String contextText = retrieveContext(input, descriptionText);

        String prompt = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(PROMPT_TEMPLATE)
                .build()
                .render(Map.of(
                        "code_changes", codeChangesText,
                        "change_description", descriptionText,
                        "code_analysis", input.codeAnalysis() != null ? input.codeAnalysis() : "(not available)",
                        "context", contextText));
        prompt = prompt + "\n\n" + outputConverter.getFormat();

        ResponseEntity<ChatResponse, PlanningAgentOutput> response = chatClient
                .prompt(prompt)
                .advisors(new SimpleLoggerAdvisor())
                .call()
                .responseEntity(PlanningAgentOutput.class);

        return response.entity();
    }

    private String retrieveContext(PlanningAgentInput input, String descriptionText) {
        List<String> queries = java.util.stream.Stream.of(descriptionText, input.codeAnalysis())
                .filter(q -> q != null && !q.isBlank())
                .toList();
        List<ContextScorePair> retrievedContexts = queryContextRetriever
                .retrieve(new ContextRetrievalRequest(queries, RETRIEVAL_CONFIGURATION));
        return String.join("\n---\n", retrievedContexts.stream()
                .map(pair -> pair.context().getContent())
                .toList());
    }
}
