package com.llmcr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ctx_review_guideline")
@DiscriminatorValue("REVIEW_GUIDELINE")
public class ReviewGuideline extends Context {

    /** general / security / performance / style */
    @Column(name = "guideline_category", length = 64)
    private String guidelineCategory;

    protected ReviewGuideline() {
    }

    public ReviewGuideline(
            Source source, int contextIndex, String name, String content, String guidelineCategory) {
        super(source, contextIndex, name, content, ContextType.REVIEW_GUIDELINE);
        this.guidelineCategory = guidelineCategory;
    }

    public String getGuidelineCategory() {
        return guidelineCategory;
    }

    public void setGuidelineCategory(String guidelineCategory) {
        this.guidelineCategory = guidelineCategory;
    }
}
