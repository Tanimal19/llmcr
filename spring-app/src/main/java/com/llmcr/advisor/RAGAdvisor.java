package com.llmcr.advisor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import com.llmcr.service.rag.ContextRetriever;
import com.llmcr.service.rag.ContextRetriever.ContextScorePair;
import com.llmcr.service.rag.ContextRetriever.RetrievalConfiguration;

import reactor.core.publisher.Flux;

@Component
public class RAGAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String RETRIEVAL_CONFIGURATION_PARAM = "rag.retrievalConfiguration";
    public static final String QUERY_LIST_PARAM = "rag.queryList";
    public static final String QUERY_TEMPLATE_PARAM = "rag.queryTemplate";
    public static final String PROMPT_TEMPLATE_PARAM = "rag.promptTemplate";
    public static final String CONTEXT_DELIMITER_PARAM = "rag.contextDelimiter";

    private static final String DEFAULT_QUERY_TEMPLATE = """
            <user_input>
            """;

    private static final String DEFAULT_PROMPT_TEMPLATE = """
            Use the following retrieved context when answering the user.

            Retrieval query: <query>

            Context:
            <contexts>

            User input:
            <user_input>
            """;

    private static final String DEFAULT_CONTEXT_DELIMITER = "\n\n---\n\n";

    private final ContextRetriever contextRetriever;
    private int order = Ordered.LOWEST_PRECEDENCE - 100;

    public RAGAdvisor(ContextRetriever contextRetriever) {
        this.contextRetriever = contextRetriever;
    }

    public RAGAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(enrichRequest(chatClientRequest));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        return streamAdvisorChain.nextStream(enrichRequest(chatClientRequest));
    }

    private ChatClientRequest enrichRequest(ChatClientRequest request) {
        String userInput = request.prompt().getUserMessage() == null ? "" : request.prompt().getUserMessage().getText();
        if (userInput == null || userInput.isBlank()) {
            return request;
        }

        RetrievalConfiguration retrievalConfiguration = contextParamAsRetrievalConfiguration(
                request,
                RETRIEVAL_CONFIGURATION_PARAM);
        if (retrievalConfiguration == null) {
            return request;
        }

        String queryTemplate = contextParamAs(request, QUERY_TEMPLATE_PARAM,
                value -> Objects.toString(value, DEFAULT_QUERY_TEMPLATE), DEFAULT_QUERY_TEMPLATE);
        String promptTemplate = contextParamAs(request, PROMPT_TEMPLATE_PARAM,
                value -> Objects.toString(value, DEFAULT_PROMPT_TEMPLATE), DEFAULT_PROMPT_TEMPLATE);
        String delimiter = contextParamAs(request, CONTEXT_DELIMITER_PARAM,
                value -> Objects.toString(value, DEFAULT_CONTEXT_DELIMITER), DEFAULT_CONTEXT_DELIMITER);

        List<String> retrievalQueries = contextParamAsStringList(request, QUERY_LIST_PARAM);
        if (retrievalQueries.isEmpty()) {
            retrievalQueries = List.of(render(queryTemplate, Map.of("user_input", userInput)));
        }

        String retrievalQuery = retrievalQueries.size() == 1
                ? retrievalQueries.get(0)
                : String.join("\n\n---\n\n", retrievalQueries);

        List<ContextScorePair> contexts = retrievalQueries.size() == 1
                ? contextRetriever.retrieve(retrievalQuery, retrievalConfiguration)
                : contextRetriever.retrieve(retrievalQueries, retrievalConfiguration);

        String mergedContexts = contexts.stream()
                .map(c -> {
                    String content = c.context().getContent() == null ? "" : c.context().getContent();
                    return "[id=%d, score=%.4f]%n%s".formatted(c.context().getId(), c.score(), content);
                })
                .filter(s -> !s.isBlank())
                .reduce((a, b) -> a + delimiter + b)
                .orElse("");

        String ragPrompt = render(promptTemplate, Map.of(
                "query", retrievalQuery,
                "contexts", mergedContexts,
                "user_input", userInput));

        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(ragPrompt))
                .build();
    }

    private String render(String template, Map<String, Object> variables) {
        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(template)
                .variables(variables)
                .build()
                .render();
    }

    private <T> T contextParamAs(ChatClientRequest request, String key, Function<Object, T> mapper, T defaultValue) {
        Object value = request.context().get(key);
        if (value == null) {
            return defaultValue;
        }

        T mappedValue;
        try {
            mappedValue = mapper.apply(value);
        } catch (Exception ex) {
            return defaultValue;
        }

        return mappedValue == null ? defaultValue : mappedValue;
    }

    private RetrievalConfiguration contextParamAsRetrievalConfiguration(ChatClientRequest request, String key) {
        return contextParamAs(request, key,
                value -> value instanceof RetrievalConfiguration retrievalConfiguration ? retrievalConfiguration : null,
                null);
    }

    private List<String> contextParamAsStringList(ChatClientRequest request, String key) {
        return contextParamAs(request, key, value -> {
            if (!(value instanceof List<?> rawList)) {
                return List.of();
            }

            return rawList.stream()
                    .filter(Objects::nonNull)
                    .map(item -> item.toString().trim())
                    .filter(text -> !text.isEmpty())
                    .toList();
        }, List.of());
    }

}
