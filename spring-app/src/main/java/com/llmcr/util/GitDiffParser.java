package com.llmcr.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitDiffParser {

    private static final Pattern GIT_DIFF_HEADER_PATTERN = Pattern.compile("^diff --git a/(.+) b/(.+)$");

    public record CodeChange(String filePath, String diffContent) {
    }

    private GitDiffParser() {
    }

    public static List<CodeChange> parseDiffFile(String diffFilePath) {
        if (diffFilePath == null || diffFilePath.isBlank()) {
            throw new IllegalArgumentException("diffFilePath cannot be null or blank");
        }

        Path path = Paths.get(diffFilePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Diff file does not exist: " + diffFilePath);
        }

        try {
            return parseDiffContent(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read diff file: " + diffFilePath, e);
        }
    }

    public static List<CodeChange> parseDiffContent(String diffContent) {
        if (diffContent == null || diffContent.isBlank()) {
            return List.of();
        }

        List<CodeChange> fileChanges = new ArrayList<>();
        String currentFilePath = null;
        StringBuilder currentDiff = new StringBuilder();

        String[] lines = diffContent.split("\\R", -1);
        for (String line : lines) {
            Matcher matcher = GIT_DIFF_HEADER_PATTERN.matcher(line);
            if (matcher.matches()) {
                appendCurrentChange(fileChanges, currentFilePath, currentDiff);
                currentFilePath = matcher.group(2);
                currentDiff.setLength(0);
            }

            if (currentFilePath != null) {
                currentDiff.append(line).append('\n');
            }
        }

        appendCurrentChange(fileChanges, currentFilePath, currentDiff);

        if (fileChanges.isEmpty()) {
            throw new IllegalArgumentException("Invalid git diff content: no 'diff --git' sections found");
        }

        return List.copyOf(fileChanges);
    }

    private static void appendCurrentChange(List<CodeChange> fileChanges, String filePath, StringBuilder diffBuilder) {
        if (filePath == null) {
            return;
        }

        String diffContent = trimTrailingNewline(diffBuilder.toString());
        fileChanges.add(new CodeChange(filePath, diffContent));
    }

    private static String trimTrailingNewline(String value) {
        if (value.endsWith("\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

}
