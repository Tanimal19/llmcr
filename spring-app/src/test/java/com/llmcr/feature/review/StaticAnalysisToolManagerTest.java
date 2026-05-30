package com.llmcr.feature.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.llmcr.feature.review.agent.tool.CheckstyleTool;
import com.llmcr.feature.review.agent.tool.PMDTool;
import com.llmcr.feature.review.agent.tool.StaticAnalysisToolManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticAnalysisToolManagerTest {

  @TempDir Path tempDir;

  @Test
  void runToolsReturnsXmlOutputForTemporaryJavaSourceDirectory() throws Exception {
    Path sourceDir = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceDir);
    Files.writeString(sourceDir.resolve("Sample.java"), SAMPLE_JAVA_CODE);

    StaticAnalysisToolManager staticAnalysisToolManager =
        new StaticAnalysisToolManager(List.of(new PMDTool(), new CheckstyleTool()));

    String analysisOutput = staticAnalysisToolManager.runStaticAnalysisTools(sourceDir);

    assertThat(analysisOutput).isNotNull().isNotBlank();
    assertThat(analysisOutput).contains("pmd Output:");
    assertThat(analysisOutput).contains("checkstyle Output:");
    assertThat(staticAnalysisToolManager.getCodeAnalysis()).isEqualTo(analysisOutput);
  }

  private static final String SAMPLE_JAVA_CODE =
      """
            package com.example;

            public class Example {
                public void print() {
                    System.out.println("SystemPrintln");
                }

                public int computeMagicNumber(int num) {
                    return num * 12 / 100;
                }
            }
            """;
}
