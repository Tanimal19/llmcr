package com.llmcr.service.review.agent.interpretation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;

import com.llmcr.client.LargeChatClient;
import com.llmcr.service.rag.RAGAdvisor;
import com.llmcr.service.review.agent.interpretation.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.service.review.agent.interpretation.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.util.GitDiffParser.CodeChange;

class InterpretationAgentTest {

    private InterpretationAgent agent;

    private ChatClient.CallResponseSpec mockCallSpec = mock(ChatClient.CallResponseSpec.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ChatClientRequestSpec mockRequestSpec = mock(ChatClientRequestSpec.class);
        when(mockRequestSpec.advisors(anyList())).thenReturn(mockRequestSpec);
        when(mockRequestSpec.advisors(any(Consumer.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallSpec);

        ChatClient mockChatClient = mock(ChatClient.class);
        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);

        LargeChatClient largeChatClient = mock(LargeChatClient.class);
        when(largeChatClient.getChatClient()).thenReturn(mockChatClient);

        RAGAdvisor mockAdvisor = mock(RAGAdvisor.class);
        RAGAdvisor.Builder mockBuilder = mock(RAGAdvisor.Builder.class);
        when(mockBuilder.retrievalConfiguration(any())).thenReturn(mockBuilder);
        when(mockBuilder.messageTemplate(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockAdvisor);

        agent = new InterpretationAgent(largeChatClient, mockBuilder);
    }

    // --- execute() ---

    @Test
    void execute_returnsOutputFromChatClient() {
        InterpretationAgentOutput expected = new InterpretationAgentOutput(
                "Added null check for user input",
                "The original code threw NullPointerException when input was null");
        when(mockCallSpec.entity(InterpretationAgentOutput.class)).thenReturn(expected);

        InterpretationAgentInput input = new InterpretationAgentInput(
                List.of(new CodeChange("src/main/java/Foo.java", "+if (input != null) { process(input); }")));

        InterpretationAgentOutput result = agent.execute(input);

        assertThat(result).isEqualTo(expected);
        assertThat(result.changeDescription()).isEqualTo("Added null check for user input");
        assertThat(result.changeMotivation()).contains("NullPointerException");
    }

    @Test
    void execute_withMultipleCodeChanges_returnsOutput() {
        InterpretationAgentOutput expected = new InterpretationAgentOutput("desc", "motivation");
        when(mockCallSpec.entity(InterpretationAgentOutput.class)).thenReturn(expected);

        InterpretationAgentInput input = new InterpretationAgentInput(List.of(
                new CodeChange("FileA.java", "+line1"),
                new CodeChange("FileB.java", "-line2")));

        InterpretationAgentOutput result = agent.execute(input);

        assertThat(result).isEqualTo(expected);
    }

    // --- InterpretationAgentInput.buildQueries() ---

    @Test
    void buildQueries_containsFilePathAndDiffForEachChange() {
        List<CodeChange> codeChanges = List.of(
                new CodeChange("Service.java", "+new method"),
                new CodeChange("Controller.java", "-old field"));

        List<String> queries = new InterpretationAgentInput(codeChanges).buildQueries();

        assertThat(queries).hasSize(2);
        assertThat(queries.get(0)).contains("Service.java").contains("+new method");
        assertThat(queries.get(1)).contains("Controller.java").contains("-old field");
    }

    @Test
    void buildQueries_singleChange_returnsSingleQuery() {
        InterpretationAgentInput input = new InterpretationAgentInput(
                List.of(new CodeChange("Foo.java", "@@ -1,3 +1,4 @@\n+import java.util.List;")));

        assertThat(input.buildQueries()).hasSize(1);
    }

    // --- InterpretationAgentInput.getTemplateVariables() ---

    @Test
    void getTemplateVariables_containsCodeChangesKey() {
        InterpretationAgentInput input = new InterpretationAgentInput(
                List.of(new CodeChange("Repo.java", "+log.info(\"hello\");")));

        Map<String, Object> variables = input.getTemplateVariables();

        assertThat(variables).containsKey("code_changes");
    }

    @Test
    void getTemplateVariables_codeChangesContainsFilePathAndDiff() {
        InterpretationAgentInput input = new InterpretationAgentInput(
                List.of(new CodeChange("UserService.java", "+validate(user);")));

        String codeChangesText = (String) input.getTemplateVariables().get("code_changes");

        assertThat(codeChangesText).contains("UserService.java").contains("+validate(user);");
    }

    @Test
    void getTemplateVariables_multipleChanges_separatedByDelimiter() {
        InterpretationAgentInput input = new InterpretationAgentInput(List.of(
                new CodeChange("A.java", "+line1"),
                new CodeChange("B.java", "+line2")));

        String codeChangesText = (String) input.getTemplateVariables().get("code_changes");

        assertThat(codeChangesText).contains("A.java").contains("B.java").contains("----");
    }

    // --- buildAdvisorParams() ---

    @Test
    void buildAdvisorParams_containsRagInput() {
        InterpretationAgentInput input = new InterpretationAgentInput(
                List.of(new CodeChange("Foo.java", "+bar")));

        Map<String, Object> params = agent.buildAdvisorParams(input);

        assertThat(params).containsKey(RAGAdvisor.RAG_INPUT);
        assertThat(params.get(RAGAdvisor.RAG_INPUT)).isSameAs(input);
    }
}
