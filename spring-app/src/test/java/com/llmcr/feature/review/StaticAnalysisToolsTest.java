package com.llmcr.feature.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticAnalysisToolsTest {

  @TempDir Path tempDir;

  @Test
  void runPmdReturnsXmlOutputForTemporaryJavaSourceDirectory() throws Exception {
    Path sourceDir = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceDir);
    Files.writeString(sourceDir.resolve("Sample.java"), SAMPLE_JAVA_CODE);
    String output = StaticAnalysisTools.runPmd(sourceDir);
    System.out.println("PMD Output:\n" + output);
    assertThat(output).isNotNull().isNotBlank();
  }

  @Test
  void runCheckstyleReturnsXmlOutputForTemporaryJavaSourceDirectory() throws Exception {
    Path sourceDir = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceDir);
    Files.writeString(sourceDir.resolve("Sample.java"), SAMPLE_JAVA_CODE);
    String output = StaticAnalysisTools.runCheckstyle(sourceDir);
    System.out.println("Checkstyle Output:\n" + output);
    assertThat(output).isNotNull().isNotBlank();
  }

  private static final String SAMPLE_JAVA_CODE =
      """
            package com.example;

            public class PMDExample {
                public void readFile() {
                    try {
                        int result = 10 / 0;
                    } catch (Exception e) {
                        // VIOLATION: Empty catch blocks are considered a code smell.
                    }
                }
            }
            """;
}
