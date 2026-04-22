package com.llmcr.service.etl.extractor;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.stereotype.Component;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;
import com.llmcr.entity.Context.ContextType;
import com.llmcr.service.etl.reader.AsciiDocumentReader;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DocumentParagraphExtractor implements SourceExtractor {

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.PDF
                || source.getType() == Source.SourceType.MARKDOWN
                || source.getType() == Source.SourceType.ASCIIDOC;
    }

    @Override
    public List<Context> apply(Source source) {
        DocumentReader reader = getReader(source);
        List<Document> docs = reader.read();

        AtomicInteger blockIndex = new AtomicInteger(0);

        return docs.stream()
                .map(doc -> new Context(
                        source,
                        blockIndex.getAndIncrement(),
                        "Paragraph::" + source.getSourceName() + "::" + blockIndex.get(),
                        doc.getText(),
                        ContextType.DOCUMENT))
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