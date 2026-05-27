package com.llmcr.feature.review;

import java.util.List;

public record CodeReviewReport(
    int prId,
    String prTitle,
    ReportContent content,
    InterpretationContent interpretation,
    List<ChecklistItem> checklistItems) {

  public record CodeChange(String filePath, String diffContent) {}

  public record ReportContent(
      String motivation,
      List<String> goodPoints,
      List<String> badPoints,
      String suggestion,
      List<ImplementationDetails> implementationDetails,
      List<Issue> issues) {}

  public record ImplementationDetails(String filename, List<String> details) {}

  public record Issue(String title, String detail, String location, String type) {}

  public record InterpretationContent(String changeDescription, String changeMotivation) {}

  public record ChecklistItem(String title, ChecklistItemAnswer answer) {}

  public record ChecklistItemAnswer(
      String finalAnswer, String analysis, List<EvidenceItem> evidence) {}

  public record EvidenceItem(String file, String lines, String reason) {}

  public String toMarkdown() {
    StringBuilder sb = new StringBuilder();

    // Summary Report section
    sb.append("# Code Review Report\n\n");
    sb.append("**PR ID:** ").append(this.prId()).append("\n\n");
    sb.append("**PR Title:** ").append(this.prTitle()).append("\n\n");

    if (this != null && this.content() != null) {
      sb.append("## Motivation\n\n");
      if (this.content().motivation() != null && !this.content().motivation().isBlank()) {
        sb.append(this.content().motivation()).append("\n\n");
      } else {
        sb.append("_No motivation provided._\n\n");
      }

      sb.append("## Good Points\n\n");
      if (this.content().goodPoints() != null && !this.content().goodPoints().isEmpty()) {
        for (String point : this.content().goodPoints()) {
          sb.append("- ").append(point).append("\n");
        }
        sb.append("\n");
      } else {
        sb.append("_No good points provided._\n\n");
      }

      sb.append("## Bad Points\n\n");
      if (this.content().badPoints() != null && !this.content().badPoints().isEmpty()) {
        for (String point : this.content().badPoints()) {
          sb.append("- ").append(point).append("\n");
        }
        sb.append("\n");
      } else {
        sb.append("_No bad points provided._\n\n");
      }

      sb.append("## Suggestion\n\n");
      if (this.content().suggestion() != null && !this.content().suggestion().isBlank()) {
        sb.append(this.content().suggestion()).append("\n\n");
      } else {
        sb.append("_No suggestion provided._\n\n");
      }

      sb.append("## Implementation Details\n\n");
      if (this.content().implementationDetails() != null
          && !this.content().implementationDetails().isEmpty()) {
        for (var detailsByFile : this.content().implementationDetails()) {
          String filename =
              detailsByFile.filename() != null ? detailsByFile.filename() : "(unknown file)";
          sb.append("#### ").append(filename).append("\n\n");
          if (detailsByFile.details() != null && !detailsByFile.details().isEmpty()) {
            for (String detail : detailsByFile.details()) {
              sb.append("- ").append(detail).append("\n");
            }
          } else {
            sb.append("- _No details provided._\n");
          }
          sb.append("\n");
        }
      }

      sb.append("## Issues\n\n");
      if (this.content().issues() != null && !this.content().issues().isEmpty()) {
        sb.append("| Type | Title | Location | Detail |\n");
        sb.append("|------|-------|----------|--------|\n");
        for (Issue issue : this.content().issues()) {
          String location = issue.location() != null ? issue.location() : "";
          String type = issue.type() != null ? issue.type() : "";
          sb.append("| ")
              .append(type)
              .append(" | ")
              .append(issue.title())
              .append(" | ")
              .append(location)
              .append(" | ")
              .append(issue.detail())
              .append(" |\n");
        }
        sb.append("\n");
      }
    } else {
      sb.append("_No summary available._\n\n");
    }

    // Appendix with interpretation results
    sb.append("\n\n");
    sb.append("# Appendix: Original Interpretation Results\n\n");
    if (this != null && this.interpretation() != null) {
      InterpretationContent interpretation = this.interpretation();
      if (interpretation.changeDescription() != null) {
        sb.append("### Change Description\n\n");
        sb.append(interpretation.changeDescription()).append("\n\n");
      }
      if (interpretation.changeMotivation() != null) {
        sb.append("### Change Motivation\n\n");
        sb.append(interpretation.changeMotivation()).append("\n\n");
      }
    } else {
      sb.append("_No interpretation results available._\n\n");
    }

    // Appendix with detailed checklist item answers
    sb.append("\n\n");
    sb.append("# Appendix: Detailed Checklist Item Answers\n\n");
    if (this != null && this.checklistItems() != null && !this.checklistItems().isEmpty()) {
      for (ChecklistItem itemAnswer : this.checklistItems()) {
        sb.append("### ").append(itemAnswer.title()).append("\n\n");
        sb.append(itemAnswer.answer().finalAnswer()).append("\n");
        sb.append(itemAnswer.answer().analysis()).append("\n");
        for (EvidenceItem evdience : itemAnswer.answer().evidence()) {
          sb.append("- ")
              .append(evdience.file())
              .append(":::")
              .append(evdience.lines())
              .append(":::")
              .append(evdience.reason())
              .append("\n");
        }
      }
    } else {
      sb.append("_No checklist item answers available._\n\n");
    }

    return sb.toString();
  }
}
