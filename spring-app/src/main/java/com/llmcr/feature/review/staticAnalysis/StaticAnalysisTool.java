package com.llmcr.feature.review.staticAnalysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class StaticAnalysisTool {
  private static final long COMMAND_TIMEOUT_SECONDS = Duration.ofMinutes(2).toSeconds();
  protected static final Path DEFAULT_TOOL_DIRECTORY =
      Path.of("../tools").toAbsolutePath().normalize();

  /**
   * Runs the static analysis tool on the given source directory and returns the XML output as a
   * string
   */
  public String run(Path sourceDir) {
    return executeCommandAndReadXml(getCommand(sourceDir), getToolName() + "-");
  }

  public abstract String getToolName();

  protected abstract List<String> getCommand(Path sourceDir);

  private static String executeCommandAndReadXml(List<String> command, String tempPrefix) {
    Path tempXml;
    try {
      tempXml = Files.createTempFile(tempPrefix, ".xml");
    } catch (IOException ex) {
      throw new RuntimeException("Failed to create temporary file for command output", ex);
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

      return Files.readString(tempXml, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new RuntimeException("Failed to execute command: " + String.join(" ", command), ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Command execution was interrupted: " + String.join(" ", command), ex);
    } finally {
      try {
        Files.deleteIfExists(tempXml);
      } catch (IOException ex) {
        throw new RuntimeException("Failed to delete temporary file: " + tempXml, ex);
      }
    }
  }
}
