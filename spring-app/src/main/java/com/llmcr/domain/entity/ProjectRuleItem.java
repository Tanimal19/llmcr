package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ctx_project_rule")
@DiscriminatorValue("PROJECT_RULE")
public class ProjectRuleItem extends Context {

  @Column(name = "rule_category", length = 64)
  private String ruleCategory;

  protected ProjectRuleItem() {}

  public ProjectRuleItem(
      Source source, int contextIndex, String name, String content, String ruleCategory) {
    super(source, contextIndex, name, content, ContextType.PROJECT_RULE);
    this.ruleCategory = ruleCategory;
  }

  public String getRuleCategory() {
    return ruleCategory;
  }

  public void setRuleCategory(String ruleCategory) {
    this.ruleCategory = ruleCategory;
  }
}
