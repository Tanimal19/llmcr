package com.llmcr.service.review.workflow;

import org.springframework.stereotype.Component;

import com.llmcr.service.review.agent.RetrievalAgent;
import com.llmcr.service.review.agent.RetrievalAgent.RetrievalInput;

@Component
public class RetrievalOrchestrator {

    private final RetrievalAgent retrievalAgent;

    public RetrievalOrchestrator(RetrievalAgent retrievalAgent) {
        this.retrievalAgent = retrievalAgent;
    }

    public String retrieve(String dataQuery) {
        if (dataQuery == null || dataQuery.isBlank()) {
            return "";
        }

        String response = retrievalAgent.execute(new RetrievalInput(dataQuery));
        return (response == null || response.isBlank()) ? "(no data retrieved)" : response;
    }
}
