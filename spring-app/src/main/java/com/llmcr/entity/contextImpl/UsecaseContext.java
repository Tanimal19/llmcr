package com.llmcr.entity.contextImpl;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "usecase")
public class UsecaseContext extends Context {

    @Column(name = "usecase_index", nullable = false)
    private Integer usecaseIndex;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    public UsecaseContext() {
    }

    public UsecaseContext(Source source, String usecaseCtx, String content, Integer usecaseIndex, String description) {
        this.setSource(source);
        this.setName("Ucase::" + usecaseCtx + "::" + usecaseIndex);
        this.setContent(content);
        this.setUsecaseIndex(usecaseIndex);
        this.setDescription(description);
    }

    public Integer getUsecaseIndex() {
        return usecaseIndex;
    }

    public void setUsecaseIndex(Integer usecaseIndex) {
        this.usecaseIndex = usecaseIndex;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
