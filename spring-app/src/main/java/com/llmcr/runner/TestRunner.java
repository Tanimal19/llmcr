package com.llmcr.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.llmcr.agent.RetrievalAgent;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "test")
public class TestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    private static final String DATA_QUERY = "What should I focus on when reviewing a code change that modifies the authentication logic?";

    private final RetrievalAgent retrievalAgent;

    public TestRunner(RetrievalAgent retrievalAgent) {
        this.retrievalAgent = retrievalAgent;
    }

    @Override
    public void run(String... args) {
        log.info("Starting RetrievalAgent test runner");
        log.info("Data query: {}", DATA_QUERY);

        String response = retrievalAgent.execute(DATA_QUERY);
        log.info("RetrievalAgent response:\n{}", response);
    }
}
