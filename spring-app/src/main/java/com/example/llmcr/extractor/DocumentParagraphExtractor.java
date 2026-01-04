package com.example.llmcr.extractor;

import com.example.llmcr.datasource.*;
import com.example.llmcr.entity.DocumentParagraph;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import java.util.List;
import java.util.UUID;

/**
 * Extracts DocumentParagraph objects from various data sources.
 */
@Component
public class DocumentParagraphExtractor implements VoidDataSourceExtractor<List<DocumentParagraph>> {

    @Override
    public List<DocumentParagraph> visit(CompilationUnitSource source) {
        List<DocumentParagraph> paragraphs = new ArrayList<>();

        // Extract comments as documentation paragraphs
        source.getCu().getAllComments().forEach(comment -> {
            String id = UUID.randomUUID().toString();
            String content = comment.getContent();
            String sourceName = "Compilation Unit"; // TODO: improve source naming

            if (content.trim().length() > 10) {
                DocumentParagraph paragraph = new DocumentParagraph(id, sourceName, content);
                paragraphs.add(paragraph);
            }
        });

        return paragraphs;
    }

    @Override
    public List<DocumentParagraph> visit(PdfSource source) {
        // For PDF files, we would need a PDF parsing library
        // This is a placeholder implementation
        List<DocumentParagraph> paragraphs = new ArrayList<>();
        Path filePath = source.getPath();

        try {
            String id = UUID.randomUUID().toString();
            String content = "PDF content from: " + filePath.toString(); // TODO: implement actual PDF text extraction
            DocumentParagraph paragraph = new DocumentParagraph(id, filePath.toString(), content);
            paragraphs.add(paragraph);
        } catch (Exception e) {
            System.err.println("Error processing PDF: " + source.getPath() + ", " + e.getMessage());
        }

        return paragraphs;
    }

    @Override
    public List<DocumentParagraph> visit(AsciiDocSource source) {
        return extractParagraphsFromTextFile(source.getPath());
    }

    @Override
    public List<DocumentParagraph> visit(MarkdownSource source) {
        return extractParagraphsFromTextFile(source.getPath());
    }

    private List<DocumentParagraph> extractParagraphsFromTextFile(Path filePath) {
        List<DocumentParagraph> paragraphs = new ArrayList<>();

        try {
            String content = Files.readString(filePath);

            // Split content into paragraphs (simple implementation)
            String[] parts = content.split("\n\n+"); // Split on double newlines

            for (String part : parts) {
                part = part.trim();
                if (part.length() > 20) { // Only meaningful paragraphs
                    String id = UUID.randomUUID().toString();
                    DocumentParagraph paragraph = new DocumentParagraph(id, filePath.toString(), part);
                    paragraphs.add(paragraph);
                }
            }

        } catch (Exception e) {
            System.err.println("Error reading file: " + filePath + ", " + e.getMessage());
        }

        return paragraphs;
    }
}
