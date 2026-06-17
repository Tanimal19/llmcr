package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ctx_bg_knowledge")
@DiscriminatorValue("BG_KNOWLEDGE")
public class BackgroundKnowledgeItem extends Context {

  @Column(name = "knowledge_category", length = 64)
  private String knowledgeCategory;

  protected BackgroundKnowledgeItem() {}

  public BackgroundKnowledgeItem(
      Source source, int contextIndex, String name, String content, String knowledgeCategory) {
    super(source, contextIndex, name, content, ContextType.BG_KNOWLEDGE);
    this.knowledgeCategory = knowledgeCategory;
  }

  public String getKnowledgeCategory() {
    return knowledgeCategory;
  }

  public void setKnowledgeCategory(String knowledgeCategory) {
    this.knowledgeCategory = knowledgeCategory;
  }
}
