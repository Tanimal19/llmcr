package com.llmcr.feature.sync.etl.extractor;

import com.llmcr.domain.entity.BackgroundKnowledge;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.entity.Source;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

@Component
public class BackgroundKnowledgeExtractor implements SourceExtractor {

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.PDF || source.getType() == Source.SourceType.MARKDOWN;
    }

    @Override
    public List<Context> apply(Source source) {
        DocumentReader reader = getReader(source);
        List<Document> docs = reader.read();

        AtomicInteger blockIndex = new AtomicInteger(0);
        return docs.stream()
                .<Context>map(
                        doc -> new BackgroundKnowledge(
                                source,
                                blockIndex.getAndIncrement(),
                                "Knowledge::" + source.getPath() + "::" + blockIndex.get(),
                                doc.getText(),
                                null))
                .toList();
    }

    private DocumentReader getReader(Source source) {
        if (source.getType() == Source.SourceType.MARKDOWN) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .build();
            return new MarkdownDocumentReader(new FileSystemResource(source.getPath()), config);
        }

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageExtractedTextFormatter(
                        ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(3)
                                .withNumberOfBottomTextLinesToDelete(3)
                                .withLeftAlignment(true)
                                .build())
                .withPagesPerDocument(1)
                .build();
        return new ParagraphPdfDocumentReader(new FileSystemResource(source.getPath()), config);
    }
}
