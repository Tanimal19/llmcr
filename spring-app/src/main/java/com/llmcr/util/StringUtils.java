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
        String cleaned = removeControlCharsExceptCrLfTab(text);
        cleaned = replaceSerializedAtnLiteral(cleaned);
        cleaned = collapseSpacesAndTabs(cleaned);
        cleaned = trimSpacesTabsPerLine(cleaned);
        cleaned = collapseThreeOrMoreLineBreaks(cleaned);
        return cleaned.strip();
    }

    private static String removeControlCharsExceptCrLfTab(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String replaceSerializedAtnLiteral(String text) {
        final String marker = "_serializedATN";
        final String replacement = "_serializedATN = \"<ANTLR_SERIALIZED_ATN>\";";

        StringBuilder sb = new StringBuilder(text.length());
        int cursor = 0;

        while (cursor < text.length()) {
            int markerPos = text.indexOf(marker, cursor);
            if (markerPos < 0) {
                sb.append(text, cursor, text.length());
                break;
            }

            sb.append(text, cursor, markerPos);

            int i = markerPos + marker.length();
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= text.length() || text.charAt(i) != '=') {
                sb.append(marker);
                cursor = markerPos + marker.length();
                continue;
            }

            i++;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= text.length() || text.charAt(i) != '"') {
                sb.append(marker);
                cursor = markerPos + marker.length();
                continue;
            }

            int end = text.indexOf("\";", i + 1);
            if (end < 0) {
                sb.append(marker);
                cursor = markerPos + marker.length();
                continue;
            }

            sb.append(replacement);
            cursor = end + 2;
        }

        return sb.toString();
    }

    private static String collapseSpacesAndTabs(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean inSpaceRun = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!inSpaceRun) {
                    sb.append(' ');
                    inSpaceRun = true;
                }
            } else {
                inSpaceRun = false;
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static String trimSpacesTabsPerLine(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;

        while (i < text.length()) {
            int lineStart = i;
            while (i < text.length() && text.charAt(i) != '\n') {
                i++;
            }

            int lineEnd = i;
            boolean hasLf = i < text.length() && text.charAt(i) == '\n';
            boolean hasCrBeforeLf = lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r';
            int contentEnd = hasCrBeforeLf ? lineEnd - 1 : lineEnd;

            int left = lineStart;
            while (left < contentEnd && (text.charAt(left) == ' ' || text.charAt(left) == '\t')) {
                left++;
            }

            int right = contentEnd;
            while (right > left && (text.charAt(right - 1) == ' ' || text.charAt(right - 1) == '\t')) {
                right--;
            }

            sb.append(text, left, right);
            if (hasCrBeforeLf) {
                sb.append('\r');
            }
            if (hasLf) {
                sb.append('\n');
                i++;
            }
        }

        return sb.toString();
    }

    private static String collapseThreeOrMoreLineBreaks(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;

        while (i < text.length()) {
            int tokenLen = lineBreakTokenLength(text, i);
            if (tokenLen == 0) {
                sb.append(text.charAt(i));
                i++;
                continue;
            }

            int runStart = i;
            int breaks = 0;
            while (i < text.length()) {
                int len = lineBreakTokenLength(text, i);
                if (len == 0) {
                    break;
                }
                breaks++;
                i += len;
            }

            if (breaks >= 3) {
                sb.append("\n\n");
            } else {
                sb.append(text, runStart, i);
            }
        }

        return sb.toString();
    }

    private static int lineBreakTokenLength(String text, int index) {
        char c = text.charAt(index);
        if (c == '\n') {
            return 1;
        }
        if (c == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
            return 2;
        }
        return 0;
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
