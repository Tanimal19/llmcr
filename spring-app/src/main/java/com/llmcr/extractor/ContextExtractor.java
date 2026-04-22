package com.llmcr.extractor;

import java.util.List;

import org.springframework.ai.document.Document;

import com.llmcr.entity.Context;
import com.llmcr.entity.Source;

public interface ContextExtractor {
    boolean supports(Source source);

    List<Document> extract(Source source);

    Context toContext(Document doc);
}
