package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "ctx_project_doc")
@DiscriminatorValue("PROJECT_DOC")
public class ProjectDocItem extends Context {

    @Column(name = "section", columnDefinition = "TEXT")
    private String section;

    @Column(name = "cutoff_date")
    private LocalDate cutoffDate;

    protected ProjectDocItem() {
    }

    public ProjectDocItem(
            Source source,
            int contextIndex,
            String name,
            String content,
            String section,
            LocalDate cutoffDate) {
        super(source, contextIndex, name, content, ContextType.PROJECT_DOC);
        this.section = section;
        this.cutoffDate = cutoffDate;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public LocalDate getCutoffDate() {
        return cutoffDate;
    }

    public void setCutoffDate(LocalDate cutoffDate) {
        this.cutoffDate = cutoffDate;
    }
}
