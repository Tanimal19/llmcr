package com.llmcr.feature.review;

import com.llmcr.domain.util.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class StaticAnalysisTools {

    private static final long COMMAND_TIMEOUT_SECONDS = Duration.ofMinutes(2).toSeconds();

    @Tool(description = "Run PMD check command and return XML output with execution metadata.")
    public String runPmd(
            @ToolParam(description = "Path to PMD ruleset xml file", required = true) String config,
            @ToolParam(description = "Path to target source file", required = true) String sourceFile) {
        Path configPath;
        Path sourcePath;
        try {
            configPath = validateInputFile(config, "config");
            sourcePath = validateInputFile(sourceFile, "sourceFile");
        } catch (IllegalArgumentException ex) {
            return errorJson(ex.getMessage());
        }

        String pmdExecutable = resolvePmdExecutable();
        if (pmdExecutable == null) {
            return errorJson("Cannot find PMD executable. Checked PATH and tools/pmd-bin-7.24.0/bin/pmd");
        }

        return executeCommand(
                "pmd",
                List.of(
                        pmdExecutable,
                        "check",
                        "-f",
                        "xml",
                        "-R",
                        configPath.toString(),
                        "-d",
                        sourcePath.toString()));
    }

    @Tool(description = "Run Checkstyle command with /google_checks.xml and return XML output with execution metadata.")
    public String runCheckstyle(
            @ToolParam(description = "Path to target source file", required = true) String sourceFile) {
        Path sourcePath;
        try {
            sourcePath = validateInputFile(sourceFile, "sourceFile");
        } catch (IllegalArgumentException ex) {
            return errorJson(ex.getMessage());
        }

        String checkstyleJar = resolveCheckstyleJar();
        if (checkstyleJar == null) {
            return errorJson(
                    "Cannot find checkstyle jar. Checked checkstyle.jar and tools/checkstyle-13.4.2-all.jar");
        }

        return executeCommand(
                "checkstyle",
                List.of(
                        "java",
                        "-jar",
                        checkstyleJar,
                        "-c",
                        "/google_checks.xml",
                        "-f",
                        "xml",
                        sourcePath.toString()));
    }

    private Path validateInputFile(String pathValue, String fieldName) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        Path path = Paths.get(pathValue).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(fieldName + " does not exist or is not a file: " + path);
        }
        return path;
    }

    private String executeCommand(String toolName, List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return errorJson(toolName + " command timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();

            Map<String, Object> response = new HashMap<>();
            response.put("tool", toolName);
            response.put("command", command);
            response.put("exitCode", exitCode);
            response.put("status", exitCode == 0 ? "ok" : "non_zero_exit");
            response.put("output", output);
            return StringUtils.jsonString(response);
        } catch (IOException ex) {
            return errorJson("Failed to start " + toolName + " command: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return errorJson("Interrupted while waiting for " + toolName + " command");
        } catch (Exception ex) {
            return errorJson("Unexpected error running " + toolName + " command: " + ex.getMessage());
        }
    }

    private String resolvePmdExecutable() {
        List<String> candidates = List.of(
                "tools/pmd-bin-7.24.0/bin/pmd",
                "../tools/pmd-bin-7.24.0/bin/pmd",
                "../../tools/pmd-bin-7.24.0/bin/pmd",
                "pmd");

        for (String candidate : candidates) {
            if ("pmd".equals(candidate)) {
                return candidate;
            }
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.exists(path) && Files.isRegularFile(path) && Files.isExecutable(path)) {
                return path.toString();
            }
        }
        return null;
    }

    private String resolveCheckstyleJar() {
        List<String> candidates = List.of(
                "checkstyle.jar",
                "tools/checkstyle-13.4.2-all.jar",
                "tools/checkstyle.jar",
                "../tools/checkstyle-13.4.2-all.jar",
                "../tools/checkstyle.jar",
                "../../tools/checkstyle-13.4.2-all.jar",
                "../../tools/checkstyle.jar");

        for (String candidate : candidates) {
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return path.toString();
            }
        }
        return null;
    }

    private String errorJson(String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        return StringUtils.jsonString(errorResponse);
    }
}
