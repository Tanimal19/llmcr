package com.llmcr.service.review.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.llmcr.model.LargeChatClient;
import com.llmcr.service.rag.ContextRetriever;
import com.llmcr.service.rag.ContextRetriever.RetrievalConfiguration;
import com.llmcr.service.rag.select.FixedKStrategy;

@Component
public class InterpretationAgent extends Agent<InterpretationAgent.InterpretationInput, String> {

    public record InterpretationInput(String codeChanges) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a senior software engineer performing a code review.
            You will be given a set of code changes (diff) and relevant project context.
            Your task is to write a clear, concise interpretation of what the code changes do,
            covering intent, key logic changes, and potential impact on the existing system.
            Be factual and precise. Do not evaluate quality yet — just interpret.
            """;

    private static final int RAG_TOP_K = 5;
    private static final String COLLECTION = "project-context";

    private final LargeChatClient largeChatClient;
    private final ContextRetriever contextRetriever;

    public InterpretationAgent(LargeChatClient largeChatClient, ContextRetriever contextRetriever) {
        this.largeChatClient = largeChatClient;
        this.contextRetriever = contextRetriever;
    }

    @Override
    protected ChatClient chatClient() {
        return largeChatClient.getChatClient();
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected String parseInput(InterpretationInput input) {
        RetrievalConfiguration config = new RetrievalConfiguration(
                RAG_TOP_K, COLLECTION, true, new FixedKStrategy());
        List<ContextRetriever.ContextScorePair> retrieved = contextRetriever.retrieve(List.of(input.codeChanges()),
                config);

        String contextText = retrieved.stream()
                .map(pair -> pair.context().getContent())
                .reduce((a, b) -> a + "\n\n---\n\n" + b)
                .orElse("");

        return """
                ## Project Context (retrieved)
                %s

                ## Code Changes
                %s

                ## Task
                Interpret the code changes above.
                """.formatted(contextText, input.codeChanges());
    }

    @Override
    protected String parseOutput(ChatClient.CallResponseSpec response) {
        return response.content();
    }
}
