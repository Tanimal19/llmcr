package com.example.llmcr.extractor;

import com.example.llmcr.datasource.*;
import com.example.llmcr.entity.DocumentParagraph;
import com.github.javaparser.ast.CompilationUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DocumentParagraphExtractor
        implements VoidDataSourceExtractor<List<DocumentParagraph>> {

    @Override
    public List<DocumentParagraph> visit(CompilationUnitSource source) {
        List<DocumentParagraph> result = new ArrayList<>();
        CompilationUnit cu = source.getCu();
        String ctx = "java::" + cu.getStorage().get().getFileName();
        AtomicInteger count = new AtomicInteger(0);

        cu.getAllComments().forEach(c -> {
            result.add(new DocumentParagraph(
                    UUID.randomUUID().toString(),
                    ctx + "::" + count.getAndIncrement(),
                    c.getContent().trim()));
        });

        System.out.println("Extracted " + result.size() + " comment paragraphs from Java file: "
                + cu.getStorage().get().getPath());

        return result;
    }

    @Override
    public List<DocumentParagraph> visit(PdfSource source) {
        List<DocumentParagraph> result = new ArrayList<>();
        Path path = source.getPath();

        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            String ctx = "pdf::" + path.getFileName();
            String text = new PDFTextStripper().getText(doc);
            StringBuilder chunk = new StringBuilder();
            AtomicInteger count = new AtomicInteger(0);

            for (String line : text.split("\n")) {
                if (chunk.length() + line.length() > 500 && chunk.length() > 0) {
                    result.add(new DocumentParagraph(
                            UUID.randomUUID().toString(),
                            ctx + "::" + count.getAndIncrement(),
                            chunk.toString().trim()));
                    chunk.setLength(0);
                }
                chunk.append(line).append(" ");
            }

            if (chunk.length() > 0) {
                result.add(new DocumentParagraph(
                        UUID.randomUUID().toString(),
                        ctx + "::" + count.getAndIncrement(),
                        chunk.toString().trim()));
            }
        } catch (IOException e) {
            System.err.println("PDF parse failed: " + path);
        }

        System.out.println("Extracted " + result.size() + " paragraphs from PDF: " + path);

        return result;
    }

    @Override
    public List<DocumentParagraph> visit(MarkdownSource source) {
        List<DocumentParagraph> result = new ArrayList<>();
        // TODO: implement Markdown parsing and extraction
        return result;
    }

    @Override
    public List<DocumentParagraph> visit(AsciiDocSource source) {
        List<DocumentParagraph> result = new ArrayList<>();
        Path path = source.getPath();

        try {
            String content = java.nio.file.Files.readString(path);
            Asciidoctor asciidoctor = Asciidoctor.Factory.create();
            org.asciidoctor.ast.Document document = asciidoctor.load(content,
                    org.asciidoctor.Options.builder().build());
            String ctx = "asciidoc::" + path.getFileName();

            processAsciiDocBlock(document, ctx, result);
        } catch (IOException e) {
            System.err.println("AsciiDoc parse failed: " + path);
        }

        System.out.println("Extracted " + result.size() + " paragraphs from AsciiDoc: " + path);

        return result;
    }

    private void processAsciiDocBlock(StructuralNode node, String ctx, List<DocumentParagraph> result) {
        if (node instanceof Section) {
            ctx += "::" + ((Section) node).getTitle();
        }
        System.out.println("Processing AsciiDoc block: " + ctx);

        List<StructuralNode> subSections = node.getBlocks().stream()
                .filter(b -> b instanceof Section)
                .toList();
        System.out.println("Found " + subSections.size() + " subsections in block: " + ctx);
        for (StructuralNode subSection : subSections) {
            processAsciiDocBlock(subSection, ctx, result);
        }

        List<StructuralNode> contentBlocks = node.getBlocks().stream()
                .filter(b -> b.getNodeName().equals("paragraph")
                        || b.getNodeName().equals("literal")
                        || b.getNodeName().equals("listing"))
                .toList();
        System.out.println("Found " + contentBlocks.size() + " content blocks in block: " + ctx);
        AtomicInteger count = new AtomicInteger(0);
        StringBuilder chunk = new StringBuilder();
        for (StructuralNode contentBlock : contentBlocks) {
            String text = contentBlock.getContent().toString().trim();
            System.out.println("Processing content block text of length " + text.length() + " in block: " + ctx);
            if (chunk.length() + text.length() > 1000 && chunk.length() > 0) {
                result.add(new DocumentParagraph(
                        UUID.randomUUID().toString(),
                        ctx + "::" + count.getAndIncrement(),
                        chunk.toString().trim()));
                chunk.setLength(0);
            }
            chunk.append(text).append("\n\n");
        }
        if (chunk.length() > 0) {
            result.add(new DocumentParagraph(
                    UUID.randomUUID().toString(),
                    ctx + "::" + count.getAndIncrement(),
                    chunk.toString().trim()));
        }
    }
}