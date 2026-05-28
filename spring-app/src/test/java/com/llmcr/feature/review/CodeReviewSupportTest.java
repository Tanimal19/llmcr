package com.llmcr.feature.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.feature.review.CodeReviewReport.ChecklistItem;
import com.llmcr.feature.review.CodeReviewReport.ChecklistItemAnswer;
import com.llmcr.feature.review.CodeReviewReport.CodeChange;
import com.llmcr.feature.review.CodeReviewReport.EvidenceItem;
import com.llmcr.feature.review.CodeReviewReport.ImplementationDetails;
import com.llmcr.feature.review.CodeReviewReport.InterpretationContent;
import com.llmcr.feature.review.CodeReviewReport.Issue;
import com.llmcr.feature.review.CodeReviewReport.ReportContent;
import com.llmcr.feature.review.PullRequestParser.ChangedFileEntry;
import com.llmcr.feature.review.PullRequestParser.PullRequestData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeReviewSupportTest {

  @TempDir Path tempDir;

  @Test
  void parsePullRequestDataParsesJsonFileWhenInputIsJson() throws Exception {
    Path jsonFile = tempDir.resolve("pr.json");
    Files.writeString(
        jsonFile, samplePullRequestJson("src/Main.java", "+class A {}", "class A {}"));

    PullRequestData result =
        CodeReviewSupport.parsePullRequestData(jsonFile.toString(), null, false);

    assertThat(result.prId()).isEqualTo(1001);
    assertThat(result.changedFiles()).hasSize(1);
    assertThat(result.changedFiles().get(0).path()).isEqualTo("src/Main.java");
  }

  @Test
  void parsePullRequestDataParsesJsonlFileWhenInputIsJsonl() throws Exception {
    Path jsonlFile = tempDir.resolve("pr.jsonl");
    Files.writeString(
        jsonlFile,
        samplePullRequestJsonlRecord("src/A.java", "+class A {}", "class A {}")
            + System.lineSeparator()
            + samplePullRequestJsonlRecord("src/B.java", "+class B {}", "class B {}"));

    PullRequestData result = CodeReviewSupport.parsePullRequestData(jsonlFile.toString(), 1, false);

    assertThat(result.changedFiles()).hasSize(1);
    assertThat(result.changedFiles().get(0).path()).isEqualTo("src/B.java");
  }

  @Test
  void parsePullRequestDataWrapsExceptionForInvalidInputPath() {
    assertThatThrownBy(
            () ->
                CodeReviewSupport.parsePullRequestData(
                    tempDir.resolve("missing.json").toString(), null, false))
        .isInstanceOf(APIServiceException.class)
        .satisfies(
            ex -> {
              APIServiceException apiEx = (APIServiceException) ex;
              assertThat(apiEx.getErrorCode())
                  .isEqualTo(APIServiceException.ErrorCode.REVIEW_PARSE_FAILED);
            });
  }

  @Test
  void toCodeChangesConvertsChangedFilesToCodeChanges() {
    PullRequestData prData =
        samplePullRequestData(
            List.of(new ChangedFileEntry("src/A.java", null, "+1", "class A {}")));

    List<CodeChange> codeChanges = CodeReviewSupport.toCodeChanges(prData);

    assertThat(codeChanges).hasSize(1);
    assertThat(codeChanges.get(0).filePath()).isEqualTo("src/A.java");
    assertThat(codeChanges.get(0).diffContent()).isEqualTo("+1");
  }

  @Test
  void validatePullRequestSizeThrowsWhenChangedFileCountExceedsLimit() {
    List<CodeChange> codeChanges =
        java.util.stream.IntStream.range(0, 21)
            .mapToObj(i -> new CodeChange("src/File" + i + ".java", "+x"))
            .toList();

    assertThatThrownBy(() -> CodeReviewSupport.validatePullRequestSize(codeChanges))
        .isInstanceOf(APIServiceException.class)
        .satisfies(
            ex -> {
              APIServiceException apiEx = (APIServiceException) ex;
              assertThat(apiEx.getErrorCode())
                  .isEqualTo(APIServiceException.ErrorCode.REVIEW_PR_TOO_LARGE);
            });
  }

  @Test
  void rebuildChangedJavaFilesToCacheWritesOnlyNonBlankJavaFiles() throws Exception {
    PullRequestData prData =
        samplePullRequestData(
            List.of(
                new ChangedFileEntry("src/A.java", null, "+A", "class A {}"),
                new ChangedFileEntry("src/B.txt", null, "+B", "ignored"),
                new ChangedFileEntry("src/C.java", null, "+C", "   ")));

    Path cachePath =
        CodeReviewSupport.rebuildChangedJavaFilesToCache(prData, tempDir.toString(), "run1");

    assertThat(cachePath).isEqualTo(tempDir.resolve("run1").resolve("cache-java"));
    assertThat(Files.readString(cachePath.resolve("src/A.java"))).isEqualTo("class A {}");
    assertThat(Files.exists(cachePath.resolve("src/B.txt"))).isFalse();
    assertThat(Files.exists(cachePath.resolve("src/C.java"))).isFalse();
  }

  @Test
  void rebuildChangedJavaFilesToCacheThrowsForPathTraversal() {
    PullRequestData prData =
        samplePullRequestData(
            List.of(new ChangedFileEntry("../evil.java", null, "+E", "class E {}")));

    assertThatThrownBy(
            () ->
                CodeReviewSupport.rebuildChangedJavaFilesToCache(
                    prData, tempDir.toString(), "run2"))
        .isInstanceOf(APIServiceException.class)
        .satisfies(
            ex -> {
              APIServiceException apiEx = (APIServiceException) ex;
              assertThat(apiEx.getErrorCode())
                  .isEqualTo(APIServiceException.ErrorCode.REVIEW_PIPELINE_FAILED);
            });
  }

  @Test
  void cleanupCacheDirectoryDeletesDirectoryRecursively() throws Exception {
    Path cacheDirectory = tempDir.resolve("run3/cache-java");
    Path nested = cacheDirectory.resolve("src/nested");
    Files.createDirectories(nested);
    Files.writeString(nested.resolve("A.java"), "class A {}");

    CodeReviewSupport.cleanupCacheDirectory(cacheDirectory);

    assertThat(Files.exists(cacheDirectory)).isFalse();
  }

  @Test
  void writeReportWritesJsonFile() throws Exception {
    CodeReviewReport report = sampleReport();

    Path outputPath = CodeReviewSupport.writeReport(report, tempDir.toString(), "run4");

    assertThat(outputPath).isEqualTo(tempDir.resolve("run4/review.json"));
    String content = Files.readString(outputPath);
    assertThat(content).contains("\"prId\":1001");
    assertThat(content).contains("\"prTitle\":\"Test PR\"");
  }

  private static final PullRequestData samplePullRequestData(List<ChangedFileEntry> changedFiles) {
    return new PullRequestData(
        1001,
        "https://example.com/pr/1001",
        "Test PR",
        "desc",
        false,
        false,
        false,
        List.of(),
        changedFiles);
  }

  private static final String samplePullRequestJson(String filePath, String patch, String content) {
    return """
                {
                  "pr_id": 1001,
                  "url": "https://example.com/pr/1001",
                  "title": "Test PR",
                  "pr_description": "desc",
                  "is_closed": false,
                  "is_merged": false,
                  "is_approved": false,
                  "comments": [],
                  "changed_files": [
                    {
                      "path": "%s",
                      "previous_path": null,
                      "patch": "%s",
                      "content": "%s"
                    }
                  ]
                }
                """
        .formatted(filePath, patch, content);
  }

  private static final String samplePullRequestJsonlRecord(
      String filePath, String patch, String content) {
    String template =
        "{"
            + "\"pr_id\":1001,"
            + "\"url\":\"https://example.com/pr/1001\","
            + "\"title\":\"Test PR\","
            + "\"pr_description\":\"desc\","
            + "\"is_closed\":false,"
            + "\"is_merged\":false,"
            + "\"is_approved\":false,"
            + "\"comments\":[],"
            + "\"changed_files\":[{"
            + "\"path\":\"%s\","
            + "\"previous_path\":null,"
            + "\"patch\":\"%s\","
            + "\"content\":\"%s\""
            + "}]}";
    return template.formatted(filePath, patch, content);
  }

  private static final CodeReviewReport sampleReport() {
    return new CodeReviewReport(
        1001,
        "Test PR",
        new ReportContent(
            "Improve parsing",
            List.of("Clear structure"),
            List.of("Needs more tests"),
            "Add unit tests",
            List.of(new ImplementationDetails("src/A.java", List.of("Added validation"))),
            List.of(new Issue("Null pointer risk", "Could fail on null", "src/A.java:10", "bug"))),
        new InterpretationContent("Adds validation", "Increase robustness"),
        List.of(
            new ChecklistItem(
                "Input validation",
                new ChecklistItemAnswer(
                    "yes",
                    "validated",
                    List.of(new EvidenceItem("src/A.java", "10-20", "checks null"))))));
  }
}
