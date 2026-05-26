package com.llmcr.domain.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class StringUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Changes the given object to a JSON string. If conversion fails, returns a
     * JSON string with an error message.
     */
    public static String jsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error converting response to JSON");
            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception ex) {
                return "{\"error\":\"Failed to serialize response\"}";
            }
        }
    }

    /**
     * clean markdown code block (```json ... ```)
     */
    public static String cleanMarkdownCodeBlocks(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        text = text.trim();

        // remove starting ``` and optional language identifier
        if (text.startsWith("```")) {
            String[] lines = text.split("\n", 2);
            String firstLine = lines[0].trim();
            if (firstLine.length() > 3) {
                text = lines.length > 1 ? lines[1] : "";
            } else {
                text = text.substring(3);
            }
        }

        // remove ending ```
        while (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }

        return text.trim();
    }
}
