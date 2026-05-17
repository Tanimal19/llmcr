package com.llmcr.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.agent.PromptRetrievalAgent;

/**
 * This is a test runner that could be modify to run some quick tests on the
 * agent during development. It is not meant for production use.
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "test")
public class TestRunner implements CommandLineRunner {

    private final PromptRetrievalAgent agent;

    public TestRunner(PromptRetrievalAgent agent) {
        this.agent = agent;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String query = "Please provide details on the concurrent streaming scenarios and how the AtomicBoolean variables are accessed and modified within the specific context of the provided code changes.";
        agent.execute(query);
    }
}
