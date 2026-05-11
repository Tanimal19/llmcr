package com.llmcr.service.etl.extractor;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.service.etl.reader.AsciiDocumentReader;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DocumentParagraphExtractor implements SourceExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentParagraphExtractor.class);

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.PDF
                || source.getType() == Source.SourceType.MARKDOWN
                || source.getType() == Source.SourceType.ASCIIDOC;
    }

    @Override
    public List<Context> apply(Source source) {
        List<Document> docs;

        if (source.getType() == Source.SourceType.PDF) {
            docs = readPdfWithFallback(source);
        } else {
            DocumentReader reader = getReader(source);
            docs = reader.read();
        }

        AtomicInteger blockIndex = new AtomicInteger(0);

        return docs.stream()
                .map(doc -> new Context(
                        source,
                        blockIndex.getAndIncrement(),
                        "Paragraph::" + source.getPath() + "::" + blockIndex.get(),
                        doc.getText(),
                        ContextType.DOCUMENT))
                .toList();
    }

    private List<Document> readPdfWithFallback(Source source) {
        try {
            log.info("Attempting to read PDF with ParagraphPdfDocumentReader: {}", source.getPath());
            DocumentReader reader = createParagraphPdfReader(source);
            return reader.read();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("table of contents")) {
                log.warn(
                        "ParagraphPdfDocumentReader failed due to missing TOC, falling back to PagePdfDocumentReader: {}",
                        source.getPath());
                DocumentReader fallbackReader = createPagePdfReader(source);
                return fallbackReader.read();
            }
            throw e;
        } catch (Exception e) {
            log.warn("ParagraphPdfDocumentReader failed, falling back to PagePdfDocumentReader: {} - Error: {}",
                    source.getPath(), e.getMessage());
            try {
                DocumentReader fallbackReader = createPagePdfReader(source);
                return fallbackReader.read();
            } catch (Exception fallbackException) {
                log.error("Both ParagraphPdfDocumentReader and PagePdfDocumentReader failed for: {}",
                        source.getPath(), fallbackException);
                throw new RuntimeException("Failed to read PDF with both readers", fallbackException);
            }
        }
    }

    private DocumentReader createParagraphPdfReader(Source source) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withNumberOfTopTextLinesToDelete(3)
                        .withNumberOfBottomTextLinesToDelete(3)
                        .withLeftAlignment(true)
                        .build())
                .withPagesPerDocument(1)
                .build();
        return new ParagraphPdfDocumentReader(new FileSystemResource(source.getPath()), config);
    }

    private DocumentReader createPagePdfReader(Source source) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withNumberOfTopTextLinesToDelete(3)
                        .withNumberOfBottomTextLinesToDelete(3)
                        .withLeftAlignment(true)
                        .build())
                .withPagesPerDocument(1)
                .build();
        return new PagePdfDocumentReader(new FileSystemResource(source.getPath()), config);
    }

    private DocumentReader getReader(Source source) {
        if (source.getType() == Source.SourceType.MARKDOWN) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .build();
            return new MarkdownDocumentReader(new FileSystemResource(source.getPath()), config);

        } else if (source.getType() == Source.SourceType.ASCIIDOC) {
            return new AsciiDocumentReader(source.getPath());

        } else {
            throw new IllegalArgumentException("Unsupported source type: " + source.getType());
        }
    }

}