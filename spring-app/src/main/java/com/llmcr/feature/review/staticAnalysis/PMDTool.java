package com.llmcr.feature.review.staticAnalysis;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PMDTool extends StaticAnalysisTool {

  @Override
  public String getToolName() {
    return "pmd";
  }

  @Override
  protected List<String> getCommand(Path sourceDir) {
    Path configFilePath =
        DEFAULT_TOOL_DIRECTORY.resolve("pmd.config.xml").toAbsolutePath().normalize();
    Path pmdExecutable =
        DEFAULT_TOOL_DIRECTORY.resolve("pmd-bin-7.24.0/bin/pmd").toAbsolutePath().normalize();

    return List.of(
        pmdExecutable.toString(),
        "check",
        "-f",
        "xml",
        "-R",
        configFilePath.toString(),
        "-d",
        sourceDir.toString(),
        "--no-fail-on-violation",
        "--no-progress",
        "--minimum-priority",
        "2");
  }
}
