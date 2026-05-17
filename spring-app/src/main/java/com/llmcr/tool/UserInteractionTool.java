package com.llmcr.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class UserInteractionTool {

    private static final Logger logger = LoggerFactory.getLogger(UserInteractionTool.class);

    private static final Lock CLI_LOCK = new ReentrantLock();

    private final BufferedReader stdinReader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

    @Tool(description = "Ask human developer a question. Use this only when you have exhausted all available tools and still cannot get the information needed to answer the user's query.")
    public String askUserQuestion(
            @ToolParam(description = "The exact question to ask. Provide context for easy understanding.") String question) {
        logger.info("[ToolCall] tool=askUserQuestion question={}", question);

        if (question == null || question.isBlank()) {
            return "(tool error: question must not be empty)";
        }

        CLI_LOCK.lock();
        try {
            System.out.println("\n[RetrievalAgent] Question for user:\n" + question);
            System.out.print("[User answer] > ");
            String answer = stdinReader.readLine();
            if (answer == null || answer.isBlank()) {
                return "(no user answer provided)";
            }
            return answer.trim();
        } catch (IOException e) {
            return "(tool error: failed to read user input: " + e.getMessage() + ")";
        } finally {
            CLI_LOCK.unlock();
        }
    }
}