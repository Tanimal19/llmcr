package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "ctx_project_issue")
@DiscriminatorValue("PROJECT_ISSUE")
public class ProjectIssue extends Context {

  @Column(name = "issue_number")
  private Integer issueNumber;

  @Column(name = "issue_title", columnDefinition = "TEXT")
  private String issueTitle;

  @Column(name = "closed", nullable = false)
  private boolean closed = false;

  @Column(name = "date")
  private LocalDate date;

  protected ProjectIssue() {}

  public ProjectIssue(
      Source source,
      int contextIndex,
      String name,
      String content,
      Integer issueNumber,
      String issueTitle,
      boolean closed,
      LocalDate date) {
    super(source, contextIndex, name, content, ContextType.PROJECT_ISSUE);
    this.issueNumber = issueNumber;
    this.issueTitle = issueTitle;
    this.closed = closed;
    this.date = date;
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

  public boolean isClosed() {
    return closed;
  }

  public void setClosed(boolean closed) {
    this.closed = closed;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }
}
