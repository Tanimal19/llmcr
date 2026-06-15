package com.llmcr.feature.review;

import java.util.List;

/**
 *
 *
 * <ul>
 *   <li>prId: pull request number
 *   <li>prTitle: pull request title
 *   <li>interpretation: LLM's interpretation of the code change
 *   <li>content: structured review result
 *   <li>staticAnalysisResults: raw output from static analysis tools
 * </ul>
 */
public record CodeReviewReport(
    int prId,
    String prTitle,
    InterpretationContent interpretation,
    ReviewReportContent content,
    String staticAnalysisResults) {

  /**
   *
   *
   * <ul>
   *   <li>filePath: path of the changed file
   *   <li>diffContent: diff text for this file
   * </ul>
   */
  public record CodeChange(String filePath, String diffContent) {
    public String toString() {
      return "File: " + filePath + "\nDiff:\n" + diffContent;
    }
  }

  /**
   *
   *
   * <ul>
   *   <li>changeDescription: what did the code change do
   *   <li>changeMotivation: why the original code was insufficient and what problem the change is
   *       trying to solve
   * </ul>
   */
  public record InterpretationContent(String changeDescription, String changeMotivation) {}

  /**
   *
   *
   * <ul>
   *   <li>motivation: why the change was made, summarized from the interpretation
   *   <li>suggestion: overall suggestion for the PR
   *   <li>goodPoints: positive aspects of the change
   *   <li>badPoints: negative aspects of the change
   *   <li>implementationDetails: per-file summaries of how the change was implemented
   *   <li>issues: list of potential issues identified in the change
   * </ul>
   */
  public record ReviewReportContent(
      String motivation,
      String suggestion,
      List<String> goodPoints,
      List<String> badPoints,
      List<ImplementationDetails> implementationDetails,
      List<Issue> issues) {}

  /**
   *
   *
   * <ul>
   *   <li>dimension: Compatibility | Design | Security | Functionality | Performance |
   *       Maintainability | Readability
   *   <li>severity: Critical | Major | Minor
   *   <li>location: filename::line_number
   *   <li>title: short issue title (max 10 words)
   *   <li>description: what the issue is and why it is a problem
   *   <li>assumption: what was assumed for this issue to be valid
   * </ul>
   */
  public record IssueDraft(
      String dimension,
      String severity,
      String location,
      String title,
      String description,
      String assumption) {

    public String toString() {
      return String.format(
          "Dimension: %s\nSeverity: %s\nLocation: %s\nTitle: %s\nDescription: %s\nAssumption: %s",
          dimension, severity, location, title, description, assumption);
    }
  }

  /**
   *
   *
   * <ul>
   *   <li>location: filename::line_number
   *   <li>evidence: specific evidence supporting the issue verdict, such as code snippets or
   *       contextual information
   * </ul>
   */
  public record IssueVerdictEvidence(String location, String evidence) {}

  /**
   *
   *
   * <ul>
   *   <li>verdict: confirmed | dismissed | needs-discussion
   *   <li>confidence: high | medium | low
   *   <li>reason: one paragraph explaining the verdict
   *   <li>evidence: list of evidence supporting the verdict
   * </ul>
   */
  public record IssueVerdict(
      String verdict, String confidence, String reason, List<IssueVerdictEvidence> evidence) {}

  /**
   *
   *
   * <ul>
   *   <li>draft: the drafted issue before review
   *   <li>verdict: the final verdict on whether the issue is valid
   * </ul>
   */
  public record Issue(IssueDraft draft, IssueVerdict verdict) {}

  /**
   *
   *
   * <ul>
   *   <li>filename: file the implementation detail refers to
   *   <li>details: summary of how the change was implemented in this file
   * </ul>
   */
  public record ImplementationDetails(String filename, String details) {}
}
