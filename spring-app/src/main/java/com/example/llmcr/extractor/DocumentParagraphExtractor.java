package com.example.llmcr.extractor;

import com.example.llmcr.datasource.*;
import com.example.llmcr.entity.DocumentParagraph;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.*;
import org.asciidoctor.jruby.ast.impl.ListItemImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class DocumentParagraphExtractor
        implements VoidDataSourceExtractor<List<DocumentParagraph>> {

    private final int maxParagraphLength;

    public DocumentParagraphExtractor(int maxParagraphLength) {
        this.maxParagraphLength = maxParagraphLength;
    }

    @Override
    public List<DocumentParagraph> visit(CompilationUnitSource source) {
        // comments in java source code is already parsed into ClassNode
        return new ArrayList<>();
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
                if (chunk.length() + line.length() > maxParagraphLength && chunk.length() > 0) {
                    result.add(new DocumentParagraph(
                            UUID.randomUUID(),
                            ctx + "::" + count.getAndIncrement(),
                            chunk.toString().trim()));
                    chunk.setLength(0);
                }
                chunk.append(line).append(" ");
            }

            if (chunk.length() > 0) {
                result.add(new DocumentParagraph(
                        UUID.randomUUID(),
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

            processAsciiDocNode(document, ctx, result, 0);
        } catch (IOException e) {
            System.err.println("AsciiDoc parse failed: " + path);
        }

        System.out.println("Extracted " + result.size() + " paragraphs from AsciiDoc: " + path);

        return result;
    }

    private void processAsciiDocNode(StructuralNode node, String ctx, List<DocumentParagraph> result, int depth) {
        // System.out.println("Processing ctx: " + ctx);

        if (depth < 2) {
            // only process header levels up to 2
            List<StructuralNode> subSections = node.getBlocks().stream()
                    .filter(b -> b instanceof Section)
                    .toList();
            for (StructuralNode subSection : subSections) {
                processAsciiDocNode(subSection, ctx + "::" + subSection.getTitle(), result, depth + 1);
            }
        }

        Stream<StructuralNode> contentBlocks;
        if (depth < 2) {
            // collect only direct contents under this node
            contentBlocks = node.getBlocks().stream()
                    .filter(b -> !(b instanceof Section));
        } else {
            // collect all contents recursively
            contentBlocks = flatten(node);
        }

        // chunk contents
        AtomicInteger count = new AtomicInteger(0);
        StringBuilder chunk = new StringBuilder();

        contentBlocks.forEach(block -> {
            // System.out.println("Processing block of type: " +
            // block.getClass().getSimpleName());

            String content;
            if (block instanceof Section) {
                content = ((Section) block).getTitle();
            } else if (block instanceof org.asciidoctor.ast.List list) {
                // for list, we merge all list items into one block
                List<StructuralNode> items = list.getItems();
                StringBuilder listContent = new StringBuilder();
                for (StructuralNode item : items) {
                    listContent.append(((ListItemImpl) item).getText()).append("\n");
                }
                content = listContent.toString();
            } else {
                if (block.getContent() == null) {
                    return; // skip empty content
                }
                content = block.getContent().toString();
            }

            if (chunk.length() + content.length() > maxParagraphLength
                    && chunk.length() > 0) {

                result.add(new DocumentParagraph(
                        UUID.randomUUID(),
                        ctx + "::" + count.getAndIncrement(),
                        chunk.toString().trim()));
                chunk.setLength(0);
            }

            chunk.append(content).append("\n\n");
        });

        // flush remaining chunk
        if (chunk.length() > 0) {
            result.add(new DocumentParagraph(
                    UUID.randomUUID(),
                    ctx + "::" + count.getAndIncrement(),
                    chunk.toString().trim()));
        }
    }

    private Stream<StructuralNode> flatten(StructuralNode node) {
        if (!(node instanceof Section) || node.getBlocks().isEmpty() || node.getBlocks() == null) {
            return Stream.empty();
        }

        return node.getBlocks().stream()
                .flatMap(child -> Stream.concat(
                        Stream.of(child),
                        flatten(child)));
    }
}