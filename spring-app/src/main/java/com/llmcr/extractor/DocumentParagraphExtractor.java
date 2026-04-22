package com.llmcr.extractor;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;

import com.llmcr.entity.Source;
import com.llmcr.reader.AsciiDocumentReader;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextDocument;
import com.llmcr.entity.Context.ContextType;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DocumentParagraphExtractor implements ContextExtractor {

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.PDF
                || source.getType() == Source.SourceType.MARKDOWN
                || source.getType() == Source.SourceType.ASCIIDOC;
    }

    @Override
    public List<ContextDocument> extract(Source source) {
        DocumentReader reader = getReader(source);
        List<Document> docs = reader.read();

        AtomicInteger blockIndex = new AtomicInteger(0);

        return docs.stream()
                .map(doc -> new ContextDocument(
                        doc,
                        new Context(
                                source,
                                blockIndex.getAndIncrement(),
                                "Paragraph::" + source.getSourceName() + "::" + blockIndex.get(),
                                doc.getText(),
                                ContextType.DOCUMENT)))
                .toList();
    }

    private DocumentReader getReader(Source source) {
        if (source.getType() == Source.SourceType.PDF) {
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0)
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                            .withNumberOfTopTextLinesToDelete(0)
                            .build())
                    .withPagesPerDocument(1)
                    .build();
            return new ParagraphPdfDocumentReader(source.getPath(), config);

        } else if (source.getType() == Source.SourceType.MARKDOWN) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .build();
            return new MarkdownDocumentReader(source.getPath(), config);

        } else if (source.getType() == Source.SourceType.ASCIIDOC) {
            return new AsciiDocumentReader(source.getPath());

        } else {
            throw new IllegalArgumentException("Unsupported source type: " + source.getType());
        }
    }

}