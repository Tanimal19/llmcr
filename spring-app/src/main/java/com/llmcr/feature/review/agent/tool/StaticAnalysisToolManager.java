package com.llmcr.feature.review.agent.tool;

import java.nio.file.Path;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class StaticAnalysisToolManager {

  private final List<StaticAnalysisTool> staticAnalysisTools;

  private String latestCodeAnalysis;

  public StaticAnalysisToolManager(List<StaticAnalysisTool> staticAnalysisTools) {
    this.staticAnalysisTools = staticAnalysisTools;
  }

  @Tool(description = "Get the static analysis result such as PMD or CheckStyle violations.")
  public String getCodeAnalysis() {
    return latestCodeAnalysis == null || latestCodeAnalysis.isBlank()
        ? "No static analysis result is available"
        : latestCodeAnalysis;
  }

  public String runStaticAnalysisTools(Path sourceDir) {
    StringBuilder codeAnalysis = new StringBuilder();
    for (StaticAnalysisTool tool : staticAnalysisTools) {
      codeAnalysis.append("%s Output:\n".formatted(tool.getToolName()));
      codeAnalysis.append(tool.run(sourceDir)).append("\n");
    }
    latestCodeAnalysis = codeAnalysis.toString();
    return codeAnalysis.toString();
  }
}
