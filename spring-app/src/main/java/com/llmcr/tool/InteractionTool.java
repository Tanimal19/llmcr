package com.llmcr.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class InteractionTool {

    private static final Logger logger = LoggerFactory.getLogger(InteractionTool.class);

    public interface Interactable {
        String askFollowUp(String question);
    }

    @Tool(description = """
                Ask user a question and get the answer.
                Use this tool when you need to ask the user for clarification or additional information to better understand their query.
            """)
    public String askUserQuestion(@ToolParam(required = true) String question, ToolContext toolContext) {
        logger.info("[ToolCall] tool=askUserQuestion question={}", question);

        if (question == null || question.isBlank()) {
            return "(tool error: question must not be empty)";
        }

        try {
            Interactable caller = (Interactable) toolContext.getContext().get("caller");
            return caller.askFollowUp(question);
        } catch (Exception e) {
            logger.error("Error executing askUserQuestion tool", e);
            return "(tool error: " + e.getMessage() + ")";
        }
    }
}