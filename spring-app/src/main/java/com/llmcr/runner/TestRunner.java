package com.llmcr.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.agent.RetrievalAgent;

/**
 * This is a test runner that could be modify to run some quick tests on the
 * agent during development. It is not meant for production use.
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "test")
public class TestRunner implements CommandLineRunner {

    private final RetrievalAgent agent;

    public TestRunner(RetrievalAgent agent) {
        this.agent = agent;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String query = "Give me the content of VectorStoreDocumentRetriever.";
        agent.execute(query);
    }

}
