package com.llmcr.domain.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class StringUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SERIALIZED_ATN_MARKER = "_serializedATN";
    private static final String SERIALIZED_ATN_REPLACEMENT = "_serializedATN = \"<ANTLR_SERIALIZED_ATN>\";";

    /**
     * Cleans the input text by removing control characters (except CR, LF, and
     * TAB), replacing serialized ATN literals, collapsing multiple spaces/tabs into
     * one.
     */
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
        StringBuilder sb = new StringBuilder(text.length());
        int cursor = 0;

        while (cursor < text.length()) {
            int markerPos = text.indexOf(SERIALIZED_ATN_MARKER, cursor);
            if (markerPos < 0) {
                sb.append(text, cursor, text.length());
                break;
            }

            sb.append(text, cursor, markerPos);

            int assignmentEnd = findSerializedAtnAssignmentEnd(text, markerPos);
            if (assignmentEnd < 0) {
                sb.append(SERIALIZED_ATN_MARKER);
                cursor = markerPos + SERIALIZED_ATN_MARKER.length();
            } else {
                sb.append(SERIALIZED_ATN_REPLACEMENT);
                cursor = assignmentEnd;
            }
        }

        return sb.toString();
    }

    private static int findSerializedAtnAssignmentEnd(String text, int markerPos) {
        int i = markerPos + SERIALIZED_ATN_MARKER.length();
        i = skipWhitespace(text, i);
        if (i >= text.length() || text.charAt(i) != '=') {
            return -1;
        }

        i = skipWhitespace(text, i + 1);
        if (i >= text.length() || text.charAt(i) != '"') {
            return -1;
        }

        int end = text.indexOf("\";", i + 1);
        return end < 0 ? -1 : end + 2;
    }

    private static int skipWhitespace(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String collapseSpacesAndTabs(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean inSpaceRun = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isSpaceOrTab(c)) {
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
            while (left < contentEnd && isSpaceOrTab(text.charAt(left))) {
                left++;
            }

            int right = contentEnd;
            while (right > left && isSpaceOrTab(text.charAt(right - 1))) {
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
            int runStart = i;
            int breakCount = 0;

            while (i < text.length()) {
                int tokenLen = lineBreakLengthAt(text, i);
                if (tokenLen == 0) {
                    break;
                }
                breakCount++;
                i += tokenLen;
            }

            if (breakCount == 0) {
                sb.append(text.charAt(i));
                i++;
                continue;
            }

            if (breakCount >= 3) {
                sb.append("\n\n");
            } else {
                sb.append(text, runStart, i);
            }
        }

        return sb.toString();
    }

    private static int lineBreakLengthAt(String text, int index) {
        char c = text.charAt(index);
        if (c == '\n') {
            return 1;
        }
        if (c == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
            return 2;
        }
        return 0;
    }

    private static boolean isSpaceOrTab(char c) {
        return c == ' ' || c == '\t';
    }

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
