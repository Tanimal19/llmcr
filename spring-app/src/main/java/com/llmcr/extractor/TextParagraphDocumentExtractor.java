package com.llmcr.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.*;
import org.asciidoctor.jruby.ast.impl.ListItemImpl;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import com.llmcr.entity.Source;
import com.llmcr.entity.TextParagraph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class TextParagraphDocumentExtractor implements ContextExtractor<TextParagraph> {

    private int maxParaLength;

    public TextParagraphDocumentExtractor(int maxParaLength) {
        this.maxParaLength = maxParaLength;
    }

    public boolean supports(Source source) {
        return source.getSourceType() == Source.SourceType.PDF
                || source.getSourceType() == Source.SourceType.MARKDOWN
                || source.getSourceType() == Source.SourceType.ASCIIDOC;
    }

    public List<TextParagraph> extract(Source source) {
        if (source.getSourceType() == Source.SourceType.PDF) {
            return extractFromPdf(Path.of(source.getSourcePath()), source);
        } else if (source.getSourceType() == Source.SourceType.ASCIIDOC) {
            return extractFromAsciiDoc(Path.of(source.getSourcePath()), source);
        } else if (source.getSourceType() == Source.SourceType.MARKDOWN) {
            return extractFromMarkdown(Path.of(source.getSourcePath()), source);
        } else {
            // fallback for unknown text-based formats
            return extractFromAsciiDoc(Path.of(source.getSourcePath()), source);
        }
    }

    /**
     * Extract text paragraphs from a PDF document, chunking by maxParaLength.
     * TODO: implement section-based extraction
     */
    public List<TextParagraph> extractFromPdf(Path path, Source source) {
        List<TextParagraph> result = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            String ctx = source.getSourceName();
            String text = new PDFTextStripper().getText(doc);
            StringBuilder paragraph = new StringBuilder();
            AtomicInteger index = new AtomicInteger(0);

            for (String line : text.split("\n")) {
                if (paragraph.length() + line.length() > maxParaLength && paragraph.length() > 0) {
                    result.add(new TextParagraph(
                            source,
                            ctx + "::" + index.get(),
                            paragraph.toString().trim(),
                            index.get(),
                            TextParagraph.ParagraphType.DOCUMENT));
                    paragraph.setLength(0);
                    index.incrementAndGet();
                }
                paragraph.append(line).append(" ");
            }

            if (paragraph.length() > 0) {
                result.add(new TextParagraph(
                        source,
                        ctx + "::" + index.get(),
                        paragraph.toString().trim(),
                        index.get(),
                        TextParagraph.ParagraphType.DOCUMENT));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Extract text paragraphs from a Markdown document, using section titles as
     * context.
     * TODO: implement it
     */
    public List<TextParagraph> extractFromMarkdown(Path path, Source source) {
        List<TextParagraph> result = new ArrayList<>();

        try {
            String content = java.nio.file.Files.readString(path);
            Parser parser = Parser.builder().build();
            Node document = parser.parse(content);

            MarkdownSection root = buildMarkdownSectionTree(document);
            processMarkdownSection(source, root, source.getSourceName(), result, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Extract text paragraphs from an AsciiDoc document, using section titles as
     * context.
     */
    public List<TextParagraph> extractFromAsciiDoc(Path path, Source source) {
        List<TextParagraph> result = new ArrayList<>();

        try {
            String content = java.nio.file.Files.readString(path);
            Asciidoctor asciidoctor = Asciidoctor.Factory.create();
            org.asciidoctor.ast.Document document = asciidoctor.load(
                    content,
                    org.asciidoctor.Options.builder().build());

            processAsciiDocNode(source, document, source.getSourceName(), result, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void processAsciiDocNode(Source source, StructuralNode node, String ctx, List<TextParagraph> result,
            int depth) {
        processSubSections(source, node, ctx, result, depth);

        Stream<StructuralNode> contentBlocks = getContentBlocks(node, depth);
        AtomicInteger index = new AtomicInteger(0);
        StringBuilder paragraph = new StringBuilder();

        contentBlocks.forEach(block -> {
            String content = extractBlockContent(block);
            if (content == null) {
                return;
            }

            flushParagraphIfNeeded(source, ctx, result, paragraph, index, content);

            paragraph.append(content).append("\n\n");
        });

        if (paragraph.length() > 0) {
            addParagraph(source, ctx, result, paragraph, index);
        }
    }

    private void processSubSections(Source source, StructuralNode node, String ctx, List<TextParagraph> result,
            int depth) {
        if (depth >= 2) {
            return;
        }

        List<StructuralNode> subSections = node.getBlocks().stream()
                .filter(b -> b instanceof Section)
                .toList();

        for (StructuralNode subSection : subSections) {
            processAsciiDocNode(source, subSection, ctx + "::" + subSection.getTitle(), result, depth + 1);
        }
    }

    private Stream<StructuralNode> getContentBlocks(StructuralNode node, int depth) {
        if (depth < 2) {
            return node.getBlocks().stream()
                    .filter(b -> !(b instanceof Section));
        }

        return flatten(node);
    }

    private String extractBlockContent(StructuralNode block) {
        if (block instanceof Section) {
            return ((Section) block).getTitle();
        }

        if (block instanceof org.asciidoctor.ast.List list) {
            return mergeListItems(list);
        }

        if (block.getContent() == null) {
            return null;
        }

        return block.getContent().toString();
    }

    private String mergeListItems(org.asciidoctor.ast.List list) {
        StringBuilder listContent = new StringBuilder();
        for (StructuralNode item : list.getItems()) {
            listContent.append(((ListItemImpl) item).getText()).append("\n");
        }
        return listContent.toString();
    }

    private void flushParagraphIfNeeded(Source source, String ctx, List<TextParagraph> result, StringBuilder paragraph,
            AtomicInteger index, String content) {
        if (paragraph.length() + content.length() > maxParaLength
                && paragraph.length() > 0) {
            addParagraph(source, ctx, result, paragraph, index);
            paragraph.setLength(0);
            index.incrementAndGet();
        }
    }

    private void addParagraph(Source source, String ctx, List<TextParagraph> result, StringBuilder paragraph,
            AtomicInteger index) {
        result.add(new TextParagraph(
                source,
                ctx + "::" + index.get(),
                paragraph.toString().trim(),
                index.get(),
                TextParagraph.ParagraphType.DOCUMENT));
    }

    private MarkdownSection buildMarkdownSectionTree(Node document) {
        MarkdownSection root = new MarkdownSection("", 0);
        Deque<MarkdownSection> sectionStack = new LinkedList<>();
        sectionStack.push(root);

        for (Node current = document.getFirstChild(); current != null; current = current.getNext()) {
            if (current instanceof Heading heading) {
                MarkdownSection section = new MarkdownSection(extractInlineText(heading), heading.getLevel());
                while (!sectionStack.isEmpty() && sectionStack.peek().level >= section.level) {
                    sectionStack.pop();
                }
                if (sectionStack.isEmpty()) {
                    sectionStack.push(root);
                }
                sectionStack.peek().children.add(section);
                sectionStack.push(section);
                continue;
            }

            sectionStack.peek().contentNodes.add(current);
        }

        return root;
    }

    private void processMarkdownSection(Source source, MarkdownSection section, String ctx, List<TextParagraph> result,
            int depth) {
        processMarkdownSubSections(source, section, ctx, result, depth);

        List<String> contentBlocks = getMarkdownContentBlocks(section, depth);
        AtomicInteger index = new AtomicInteger(0);
        StringBuilder paragraph = new StringBuilder();

        for (String content : contentBlocks) {
            if (content == null || content.isBlank()) {
                continue;
            }

            flushParagraphIfNeeded(source, ctx, result, paragraph, index, content);
            paragraph.append(content).append("\n\n");
        }

        if (paragraph.length() > 0) {
            addParagraph(source, ctx, result, paragraph, index);
        }
    }

    private void processMarkdownSubSections(Source source, MarkdownSection section, String ctx,
            List<TextParagraph> result, int depth) {
        if (depth >= 2) {
            return;
        }

        for (MarkdownSection child : section.children) {
            processMarkdownSection(source, child, ctx + "::" + child.title, result, depth + 1);
        }
    }

    private List<String> getMarkdownContentBlocks(MarkdownSection section, int depth) {
        if (depth < 2) {
            return section.contentNodes.stream()
                    .map(this::extractMarkdownNodeContent)
                    .toList();
        }

        List<String> flattened = new ArrayList<>();
        flattenMarkdownSectionContent(section, flattened);
        return flattened;
    }

    private void flattenMarkdownSectionContent(MarkdownSection section, List<String> flattened) {
        for (MarkdownSection child : section.children) {
            if (child.title != null && !child.title.isBlank()) {
                flattened.add(child.title);
            }

            for (Node node : child.contentNodes) {
                flattened.add(extractMarkdownNodeContent(node));
            }

            flattenMarkdownSectionContent(child, flattened);
        }
    }

    private String extractMarkdownNodeContent(Node node) {
        if (node == null || node instanceof ThematicBreak) {
            return null;
        }

        if (node instanceof FencedCodeBlock fencedCodeBlock) {
            return fencedCodeBlock.getLiteral();
        }

        if (node instanceof BulletList || node instanceof OrderedList) {
            return mergeMarkdownListItems(node);
        }

        return extractInlineText(node);
    }

    private String mergeMarkdownListItems(Node listNode) {
        StringBuilder listContent = new StringBuilder();
        for (Node item = listNode.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) {
                continue;
            }

            String itemText = extractInlineText(item).trim();
            if (!itemText.isBlank()) {
                listContent.append(itemText).append("\n");
            }
        }
        return listContent.toString();
    }

    private String extractInlineText(Node node) {
        StringBuilder text = new StringBuilder();
        appendInlineText(node, text);
        return text.toString();
    }

    private void appendInlineText(Node node, StringBuilder out) {
        if (node == null) {
            return;
        }

        if (node instanceof Text text) {
            out.append(text.getLiteral());
        } else if (node instanceof Code code) {
            out.append(code.getLiteral());
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            out.append("\n");
        } else if (node instanceof FencedCodeBlock fencedCodeBlock) {
            out.append(fencedCodeBlock.getLiteral());
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            appendInlineText(child, out);
        }
    }

    private Stream<StructuralNode> flatten(StructuralNode node) {
        if (!(node instanceof Section) || node.getBlocks() == null || node.getBlocks().isEmpty()) {
            return Stream.empty();
        }

        return node.getBlocks().stream()
                .flatMap(child -> Stream.concat(
                        Stream.of(child),
                        flatten(child)));
    }

    private static final class MarkdownSection {
        private final String title;
        private final int level;
        private final List<Node> contentNodes = new ArrayList<>();
        private final List<MarkdownSection> children = new ArrayList<>();

        private MarkdownSection(String title, int level) {
            this.title = title;
            this.level = level;
        }
    }
}