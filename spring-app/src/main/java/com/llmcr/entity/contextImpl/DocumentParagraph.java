package com.llmcr.entity.contextImpl;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_paragraph")
public class DocumentParagraph extends Context {

    @Column(name = "paragraph_index", nullable = false)
    private Integer paragraphIndex;

    public DocumentParagraph() {
    }

    public DocumentParagraph(Source source, String docName, String content, Integer paragraphIndex) {
        this.setSource(source);
        this.setContextName("Doc::" + docName + "::" + paragraphIndex);
        this.setContent(content);
        this.setParagraphIndex(paragraphIndex);
    }

    public Integer getParagraphIndex() {
        return paragraphIndex;
    }

    public void setParagraphIndex(Integer paragraphIndex) {
        this.paragraphIndex = paragraphIndex;
    }
}
