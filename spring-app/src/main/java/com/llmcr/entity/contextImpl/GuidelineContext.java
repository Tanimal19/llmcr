package com.llmcr.entity.contextImpl;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "guideline")
public class GuidelineContext extends Context {

    @Column(name = "guideline_index", nullable = false)
    private Integer guidelineIndex;

    public GuidelineContext() {
    }

    public GuidelineContext(Source source, String guidelineCtx, String content, Integer guidelineIndex) {
        this.setSource(source);
        this.setContextName("Guide::" + guidelineCtx + "::" + guidelineIndex);
        this.setContent(content);
        this.setGuidelineIndex(guidelineIndex);
    }

    public Integer getGuidelineIndex() {
        return guidelineIndex;
    }

    public void setGuidelineIndex(Integer guidelineIndex) {
        this.guidelineIndex = guidelineIndex;
    }
}
