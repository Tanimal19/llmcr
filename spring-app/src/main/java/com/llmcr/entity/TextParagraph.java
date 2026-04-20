package com.llmcr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "text_paragraph")
public class TextParagraph extends Context {

    @Column(name = "paragraph_index", nullable = false)
    private Integer paragraphIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "paragraph_type", nullable = false, length = 32)
    private ParagraphType paragraphType = ParagraphType.UNDEFINED;

    public enum ParagraphType {
        DOCUMENT,
        GUIDELINE,
        USECASE,
        TOOLAPI,
        UNDEFINED
    }

    public TextParagraph(Source source, String paragraphName, String content,
            Integer paragraphIndex, ParagraphType paragraphType) {
        this.setSource(source);
        this.setContextName(paragraphName);
        this.setContent(content);
        this.paragraphIndex = paragraphIndex;
        this.paragraphType = paragraphType;
    }

    public Integer getParagraphIndex() {
        return paragraphIndex;
    }

    public void setParagraphIndex(Integer paragraphIndex) {
        this.paragraphIndex = paragraphIndex;
    }

    public ParagraphType getParagraphType() {
        return paragraphType;
    }

    public void setParagraphType(ParagraphType paragraphType) {
        this.paragraphType = paragraphType;
    }
}