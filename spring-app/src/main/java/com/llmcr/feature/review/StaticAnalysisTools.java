package com.llmcr.feature.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class StaticAnalysisTools {

    private static final long COMMAND_TIMEOUT_SECONDS = Duration.ofMinutes(2).toSeconds();

    private static final Path TOOL_DIRECTORY = Path.of("../tools").toAbsolutePath().normalize();

    private static final String PMD_CONFIG_FILE_NAME = "pmd.config.xml";
    private static final String PMD_EXECUTABLE_NAME = "pmd-bin-7.24.0/bin/pmd";
    private static final String CHECKSTYLE_JAR_NAME = "checkstyle-13.4.2-all.jar";

    public static String runPmd(Path sourceDir) {
        Path configFilePath = TOOL_DIRECTORY.resolve(PMD_CONFIG_FILE_NAME).toAbsolutePath().normalize();
        Path pmdExecutable = TOOL_DIRECTORY.resolve(PMD_EXECUTABLE_NAME).toAbsolutePath().normalize();
        if (pmdExecutable == null) {
            return null;
        }

        return executeCommandAndReadXml(
                List.of(
                        pmdExecutable.toString(),
                        "check",
                        "-f",
                        "xml",
                        "-R",
                        configFilePath.toString(),
                        "-d",
                        sourceDir.toString()),
                "pmd-");
    }

    public static String runCheckstyle(Path sourceDir) {
        Path checkstyleJar = TOOL_DIRECTORY.resolve(CHECKSTYLE_JAR_NAME).toAbsolutePath().normalize();
        if (checkstyleJar == null) {
            return null;
        }

        return executeCommandAndReadXml(
                List.of(
                        "java",
                        "-jar",
                        checkstyleJar.toString(),
                        "-c",
                        "/google_checks.xml",
                        "-f",
                        "xml",
                        sourceDir.toString()),
                "checkstyle-");
    }

    private static String executeCommandAndReadXml(List<String> command, String tempPrefix) {
        Path tempXml;
        try {
            tempXml = Files.createTempFile(tempPrefix, ".xml");
        } catch (IOException ex) {
            return null;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectOutput(tempXml.toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            if (process.exitValue() != 0) {
                return null;
            }

            return Files.readString(tempXml, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            try {
                Files.deleteIfExists(tempXml);
            } catch (IOException ex) {
                return null;
            }
        }
    }
}
