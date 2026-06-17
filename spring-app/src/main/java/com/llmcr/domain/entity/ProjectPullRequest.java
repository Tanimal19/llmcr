package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "ctx_project_pr")
@DiscriminatorValue("PROJECT_PR")
public class ProjectPullRequest extends Context {

  @Column(name = "pr_number")
  private Integer prNumber;

  @Column(name = "pr_title", columnDefinition = "TEXT")
  private String prTitle;

  /** merged / closed / opened */
  @Column(name = "result", length = 16)
  private String result;

  @Column(name = "date")
  private LocalDate date;

  protected ProjectPullRequest() {}

  public ProjectPullRequest(
      Source source,
      int contextIndex,
      String name,
      String content,
      Integer prNumber,
      String prTitle,
      String result,
      LocalDate date) {
    super(source, contextIndex, name, content, ContextType.PROJECT_PR);
    this.prNumber = prNumber;
    this.prTitle = prTitle;
    this.result = result;
    this.date = date;
  }

  public Integer getPrNumber() {
    return prNumber;
  }

  public void setPrNumber(Integer prNumber) {
    this.prNumber = prNumber;
  }

  public String getPrTitle() {
    return prTitle;
  }

  public void setPrTitle(String prTitle) {
    this.prTitle = prTitle;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }
}
