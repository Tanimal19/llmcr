package com.llmcr.entity.contextImpl;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "document")
public class DocumentContext extends Context {

    @Column(name = "paragraph_index", nullable = false)
    private Integer paragraphIndex;

    public DocumentContext() {
    }

    public DocumentContext(Source source, String docName, String content, Integer paragraphIndex) {
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
