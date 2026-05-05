package com.llmcr.service.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.llmcr.service.rag.retrieval.QueryContextRetriever;
import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextRetrievalConfiguration;
import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextRetrievalRequest;
import com.llmcr.service.rag.retrieval.QueryContextRetriever.ContextScorePair;
import com.llmcr.service.rag.retrieval.select.FixedKStrategy;

public class RAGAdvisor implements BaseAdvisor {

    public static final String RAG_INPUT = "ragInput";
    public static final String RETRIEVED_CONTEXT = "retrievedContext";

    private static final String DEFAULT_MESSAGE_TEMPLATE = "Use the following context: <context>";

    private final QueryContextRetriever retriever;
    private final ContextRetrievalConfiguration retrievalConfiguration;
    private final String messageTemplate;

    private RAGAdvisor(QueryContextRetriever retriever, ContextRetrievalConfiguration retrievalConfiguration,
            String messageTemplate) {
        this.retriever = retriever;
        this.retrievalConfiguration = retrievalConfiguration != null ? retrievalConfiguration
                : new ContextRetrievalConfiguration(5, new FixedKStrategy(), "docs", false);
        this.messageTemplate = messageTemplate != null ? messageTemplate : DEFAULT_MESSAGE_TEMPLATE;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, @Nullable AdvisorChain chain) {
        Map<String, Object> context = new HashMap<>(request.context());

        // get RAGInput from request context
        Assert.isTrue(request.context().containsKey(RAG_INPUT),
                "RAGAdvisor requires a '" + RAG_INPUT + "' parameter in the request context");
        RAGInput ragInput = (RAGInput) request.context().get(RAG_INPUT);

        // retrieve relevant context
        List<String> queries = ragInput.buildQueries();
        List<ContextScorePair> retrievedContexts = retriever
                .retrieve(new ContextRetrievalRequest(queries, retrievalConfiguration));

        // render retrieved context into a message using the template
        String renderedContext = renderContext(retrievedContexts);
        context.put(RETRIEVED_CONTEXT, renderedContext);

        // insert the rendered context at the front of the user message
        String originalUserMessage = request.prompt().getUserMessage().getText();
        String newUserMessage = renderedContext + "\n\n" + originalUserMessage;

        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(newUserMessage))
                .build();
    }

    private String renderContext(List<ContextScorePair> retrievedContexts) {
        StringBuilder contextBuilder = new StringBuilder();
        for (ContextScorePair pair : retrievedContexts) {
            contextBuilder.append(pair.context().getContent()).append("\n---\n");
        }
        String fullContext = contextBuilder.toString();
        return messageTemplate.replace("<context>", fullContext);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, @Nullable AdvisorChain chain) {
        // no post-processing needed for this advisor
        return response;
    }

    @Component
    public static final class Builder {
        private final QueryContextRetriever retriever;
        private ContextRetrievalConfiguration retrievalConfiguration;
        private String messageTemplate;

        public Builder(QueryContextRetriever retriever) {
            this.retriever = retriever;
        }

        public Builder retrievalConfiguration(ContextRetrievalConfiguration retrievalConfiguration) {
            this.retrievalConfiguration = retrievalConfiguration;
            return this;
        }

        public Builder messageTemplate(String messageTemplate) {
            this.messageTemplate = messageTemplate;
            return this;
        }

        public RAGAdvisor build() {
            return new RAGAdvisor(retriever, retrievalConfiguration, messageTemplate);
        }
    }
}
