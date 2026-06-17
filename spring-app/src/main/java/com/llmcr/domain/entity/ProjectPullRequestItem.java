package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "ctx_project_pr")
@DiscriminatorValue("PROJECT_PR")
public class ProjectPullRequestItem extends Context {

  @Column(name = "pr_number")
  private Integer prNumber;

  @Column(name = "pr_title", columnDefinition = "TEXT")
  private String prTitle;

  @Column(name = "pr_status", length = 16)
  private String prStatus;

  @Column(name = "cutoff_date")
  private LocalDate cutoffDate;

  protected ProjectPullRequestItem() {}

  public ProjectPullRequestItem(
      Source source,
      int contextIndex,
      String name,
      String content,
      Integer prNumber,
      String prTitle,
      String prStatus,
      LocalDate cutoffDate) {
    super(source, contextIndex, name, content, ContextType.PROJECT_PR);
    this.prNumber = prNumber;
    this.prTitle = prTitle;
    this.prStatus = prStatus;
    this.cutoffDate = cutoffDate;
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

  public String getPrStatus() {
    return prStatus;
  }

  public void setPrStatus(String prStatus) {
    this.prStatus = prStatus;
  }

  public LocalDate getCutoffDate() {
    return cutoffDate;
  }

  public void setCutoffDate(LocalDate cutoffDate) {
    this.cutoffDate = cutoffDate;
  }
}
