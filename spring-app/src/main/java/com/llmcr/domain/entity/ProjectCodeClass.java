package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "ctx_project_code")
@DiscriminatorValue("PROJECT_CODE")
public class ProjectCodeClass extends Context {

  @Column(name = "package_name", columnDefinition = "TEXT")
  private String packageName;

  @Column(name = "class_name", columnDefinition = "TEXT")
  private String className;

  @Column(name = "is_interface", nullable = false)
  private boolean isInterface = false;

  @Column(name = "cutoff_date")
  private LocalDate cutoffDate;

  protected ProjectCodeClass() {}

  public ProjectCodeClass(
      Source source,
      int contextIndex,
      String name,
      String content,
      String packageName,
      String className,
      boolean isInterface,
      LocalDate cutoffDate) {
    super(source, contextIndex, name, content, ContextType.PROJECT_CODE);
    this.packageName = packageName;
    this.className = className;
    this.isInterface = isInterface;
    this.cutoffDate = cutoffDate;
  }

  public String getPackageName() {
    return packageName;
  }

  public void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public boolean isInterface() {
    return isInterface;
  }

  public void setInterface(boolean isInterface) {
    this.isInterface = isInterface;
  }

  public LocalDate getCutoffDate() {
    return cutoffDate;
  }

  public void setCutoffDate(LocalDate cutoffDate) {
    this.cutoffDate = cutoffDate;
  }
}
