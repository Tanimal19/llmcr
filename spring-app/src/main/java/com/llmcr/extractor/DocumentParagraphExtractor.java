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
    public List<Document> extract(Source source) {
        DocumentReader reader = getReader(source);
        List<Document> docs = reader.read();

        AtomicInteger blockIndex = new AtomicInteger(0);
        docs.forEach(doc -> {
            doc.getMetadata().put("source", source);
            doc.getMetadata().put("contextIndex", blockIndex.getAndIncrement());
        });

        return docs;
    }

    @Override
    public Context toContext(Document doc) {
        assert doc.getMetadata().containsKey("source") : "Document metadata must contain 'source'";
        assert doc.getMetadata().containsKey("contextIndex") : "Document metadata must contain 'contextIndex'";

        Source source = (Source) doc.getMetadata().get("source");
        int contextIndex = (int) doc.getMetadata().get("contextIndex");
        return new Context(
                source,
                contextIndex,
                "Document::" + source.getSourceName() + "::" + contextIndex,
                doc.getText(),
                ContextType.DOCUMENT);
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