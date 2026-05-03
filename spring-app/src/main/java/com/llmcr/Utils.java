package com.llmcr;

public class Utils {
    public static String stringFilter(String text) {
        if (text == null) {
            return "";
        }
        return text
                // remove control characters except newlines and tabs
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                // specific cleaning for ANTLR serialized ATN
                .replaceAll("_serializedATN\\s*=\\s*\"[\\s\\S]*?\";",
                        "_serializedATN = \"<ANTLR_SERIALIZED_ATN>\";")
                // collapse runs of spaces/tabs (but not newlines) into a single space
                .replaceAll("[ \\t]+", " ")
                // trim leading/trailing whitespace on each line
                .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "")
                // collapse 3+ consecutive blank lines into 2
                .replaceAll("(\\r?\\n){3,}", "\n\n")
                .strip();
    }
}
