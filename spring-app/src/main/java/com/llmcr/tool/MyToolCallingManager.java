package com.llmcr.tool;

import com.llmcr.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

public class MyToolCallingManager {

    private static final Logger logger = LoggerFactory.getLogger(MyToolCallingManager.class);
    private final Map<String, ToolCallback> avaliableToolCallbacks;

    public record ToolCall(String toolName, Map<String, Object> arguments) {
        public String toString() {
            StringJoiner joiner = new StringJoiner(", ");
            for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                joiner.add(String.valueOf(entry.getValue()));
            }
            return toolName + "(" + joiner + ")";
        }
    }

    public MyToolCallingManager(ToolCallback[] toolCallbacks) {
        this.avaliableToolCallbacks = new HashMap<>();
        for (ToolCallback callback : toolCallbacks) {
            this.avaliableToolCallbacks.put(callback.getToolDefinition().name(), callback);
        }
    }

    public String executeToolCall(ToolCall toolCall) {
        ToolCallback callback = avaliableToolCallbacks.get(toolCall.toolName());
        if (callback == null) {
            return "Tool not found: " + toolCall.toolName();
        }

        logger.info("[ToolCall] tool={} arguments={}", toolCall.toolName(), toolCall.arguments());

        return callback.call(StringUtils.jsonString(toolCall.arguments()));
    }
}
