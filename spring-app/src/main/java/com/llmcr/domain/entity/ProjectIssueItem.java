package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "ctx_project_issue")
@DiscriminatorValue("PROJECT_ISSUE")
public class ProjectIssueItem extends Context {

  @Column(name = "issue_number")
  private Integer issueNumber;

  @Column(name = "issue_title", columnDefinition = "TEXT")
  private String issueTitle;

  @Column(name = "issue_status", length = 16)
  private String issueStatus;

  @Column(name = "cutoff_date")
  private LocalDate cutoffDate;

  protected ProjectIssueItem() {}

  public ProjectIssueItem(
      Source source,
      int contextIndex,
      String name,
      String content,
      Integer issueNumber,
      String issueTitle,
      String issueStatus,
      LocalDate cutoffDate) {
    super(source, contextIndex, name, content, ContextType.PROJECT_ISSUE);
    this.issueNumber = issueNumber;
    this.issueTitle = issueTitle;
    this.issueStatus = issueStatus;
    this.cutoffDate = cutoffDate;
  }

  public Integer getIssueNumber() {
    return issueNumber;
  }

  public void setIssueNumber(Integer issueNumber) {
    this.issueNumber = issueNumber;
  }

  public String getIssueTitle() {
    return issueTitle;
  }

  public void setIssueTitle(String issueTitle) {
    this.issueTitle = issueTitle;
  }

  public String getIssueStatus() {
    return issueStatus;
  }

  public void setIssueStatus(String issueStatus) {
    this.issueStatus = issueStatus;
  }

  public LocalDate getCutoffDate() {
    return cutoffDate;
  }

  public void setCutoffDate(LocalDate cutoffDate) {
    this.cutoffDate = cutoffDate;
  }
}
