package com.llmcr.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.llmcr.agent.QuestionAnswerAgent;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "question_answer")
public class QuestionAnswerRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(QuestionAnswerRunner.class);

    private final QuestionAnswerAgent questionAnswerAgent;

    public QuestionAnswerRunner(QuestionAnswerAgent questionAnswerAgent) {
        this.questionAnswerAgent = questionAnswerAgent;
    }

    @Override
    public void run(String... args) throws Exception {
        // Filter out Spring configuration parameters (--app.mode=...)
        java.util.List<String> filteredArgs = new java.util.ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("--app.")) {
                filteredArgs.add(arg);
            }
        }
        String query = String.join(" ", filteredArgs).trim();

        if (query.isEmpty()) {
            return;
        }

        logger.info("Running question-answer agent for query: {}", query);
        String answer = questionAnswerAgent.execute(query);
        logger.info("\nFinal Answer:\n" + answer);
    }
}
