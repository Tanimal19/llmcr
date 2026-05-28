package com.llmcr.feature.review.staticAnalysis;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CheckstyleTool extends StaticAnalysisTool {

  @Override
  public String getToolName() {
    return "checkstyle";
  }

  @Override
  protected List<String> getCommand(Path sourceDir) {
    Path configFilePath =
        DEFAULT_TOOL_DIRECTORY.resolve("checkstyle.config.xml").toAbsolutePath().normalize();
    Path checkstyleJar =
        DEFAULT_TOOL_DIRECTORY.resolve("checkstyle-13.4.2-all.jar").toAbsolutePath().normalize();

    return List.of(
        "java",
        "-jar",
        checkstyleJar.toString(),
        "-c",
        configFilePath.toString(),
        "-f",
        "xml",
        sourceDir.toString());
  }
}
