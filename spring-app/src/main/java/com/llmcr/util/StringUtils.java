package com.llmcr.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class StringUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text
            // remove control characters except newlines and tabs
            .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
            // specific cleaning for ANTLR serialized ATN
            .replaceAll("_serializedATN\\s*=\\s*\"[\\s\\S]*?\";", "_serializedATN = \"<ANTLR_SERIALIZED_ATN>\";")
            // collapse runs of spaces/tabs (but not newlines) into a single space
            .replaceAll("[ \\t]+", " ")
            // trim leading/trailing whitespace on each line
            .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "")
            // collapse 3+ consecutive blank lines into 2
            .replaceAll("(\\r?\\n){3,}", "\n\n")
            .strip();
    }

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
     * 清理 markdown 代碼塊格式（```json ... ```）
     * 處理各種不完整或不對稱的 markdown 格式
     */
    public static String cleanMarkdownCodeBlocks(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        text = text.trim();

        // 移除開頭的 ```json 或 ```
        if (text.startsWith("```")) {
            String[] lines = text.split("\n", 2);
            String firstLine = lines[0].trim();
            if (firstLine.length() > 3) {
                // 有語言標識符如 ```json
                text = lines.length > 1 ? lines[1] : "";
            } else {
                // 只有 ```
                text = text.substring(3);
            }
        }

        // 移除結尾的 ```（只要有就移除，不需要開頭也有）
        while (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }

        return text.trim();
    }
}
