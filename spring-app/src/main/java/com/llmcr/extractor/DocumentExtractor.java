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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.llmcr.entity.Source;
import com.llmcr.entity.contextImpl.DocumentContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;

public class DocumentExtractor implements ContextExtractor<DocumentContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentExtractor.class);

    private int maxParaLength;

    public DocumentExtractor(int maxParaLength) {
        this.maxParaLength = maxParaLength;
    }

    public boolean supports(Source source) {
        return source.getSourceType() == Source.SourceType.PDF
                || source.getSourceType() == Source.SourceType.MARKDOWN
                || source.getSourceType() == Source.SourceType.ASCIIDOC;
    }

    public List<DocumentContext> extract(Source source) {
        if (source.getSourceType() == Source.SourceType.PDF) {
            return extractFromPdf(Path.of(source.getSourcePath()), source);
        } else if (source.getSourceType() == Source.SourceType.ASCIIDOC) {
            return extractFromAsciiDoc(Path.of(source.getSourcePath()), source);
        } else if (source.getSourceType() == Source.SourceType.MARKDOWN) {
            return extractFromMarkdown(Path.of(source.getSourcePath()), source);
        } else {
            return List.of();
        }
    }

    /**
     * Extract text paragraphs from a PDF document, chunking by maxParaLength.
     */
    public List<DocumentContext> extractFromPdf(Path path, Source source) {
        List<DocumentContext> result = new ArrayList<>();

        try {
            PdfParseUtil parseUtil = new PdfParseUtil(path, source.getSourceName(), maxParaLength);
            collectDocumentContexts(source, parseUtil, result);
        } catch (IOException e) {
            LOGGER.error("Failed to extract PDF document from path: {}", path, e);
        }
        return result;
    }

    /**
     * Extract text paragraphs from a Markdown document, using section titles as
     * context.
     */
    public List<DocumentContext> extractFromMarkdown(Path path, Source source) {
        List<DocumentContext> result = new ArrayList<>();

        try {
            MarkdownParseUtil parseUtil = new MarkdownParseUtil(path, source.getSourceName(), maxParaLength);
            collectDocumentContexts(source, parseUtil, result);
        } catch (IOException e) {
            LOGGER.error("Failed to extract Markdown document from path: {}", path, e);
        }

        return result;
    }

    /**
     * Extract text paragraphs from an AsciiDoc document, using section titles as
     * context.
     */
    public List<DocumentContext> extractFromAsciiDoc(Path path, Source source) {
        List<DocumentContext> result = new ArrayList<>();

        try {
            AsciiDocParseUtil parseUtil = new AsciiDocParseUtil(path, source.getSourceName(), maxParaLength);
            collectDocumentContexts(source, parseUtil, result);
        } catch (IOException e) {
            LOGGER.error("Failed to extract AsciiDoc document from path: {}", path, e);
        }
        return result;
    }

    private void collectDocumentContexts(Source source, BlockProvider provider, List<DocumentContext> result) {
        AtomicInteger contextIndex = new AtomicInteger(0);

        ParsedBlock block;
        while ((block = provider.getNextBlock()) != null) {
            result.add(new DocumentContext(
                    source,
                    block.ctx + "::" + contextIndex.get(),
                    block.content,
                    contextIndex.get()));
            contextIndex.incrementAndGet();
        }
    }

    private interface BlockProvider {
        ParsedBlock getNextBlock();
    }

    private static final class ParsedBlock {
        private final String ctx;
        private final String content;

        private ParsedBlock(String ctx, String content) {
            this.ctx = ctx;
            this.content = content;
        }
    }

    private static void appendChunkOrFlush(Queue<ParsedBlock> output, String ctx, StringBuilder paragraph,
            String content,
            int maxParaLength) {
        if (content == null || content.isBlank()) {
            return;
        }

        // If adding this content would exceed the max paragraph length, flush the
        // current paragraph first
        if (paragraph.length() + content.length() > maxParaLength && paragraph.length() > 0) {
            output.add(new ParsedBlock(ctx, paragraph.toString().trim()));
            paragraph.setLength(0);
        }

        paragraph.append(content).append("\n\n");
    }

    private static void flushParagraph(Queue<ParsedBlock> output, String ctx, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            output.add(new ParsedBlock(ctx, paragraph.toString().trim()));
            paragraph.setLength(0);
        }
    }

    private static final class PdfParseUtil implements BlockProvider {
        private final Queue<ParsedBlock> blocks = new LinkedList<>();

        private PdfParseUtil(Path path, String sourceName, int maxParaLength) throws IOException {
            try (PDDocument doc = Loader.loadPDF(path.toFile())) {
                String text = new PDFTextStripper().getText(doc);
                StringBuilder paragraph = new StringBuilder();

                for (String line : text.split("\n")) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }

                    if (paragraph.length() + line.length() > maxParaLength && paragraph.length() > 0) {
                        blocks.add(new ParsedBlock(sourceName, paragraph.toString().trim()));
                        paragraph.setLength(0);
                    }

                    paragraph.append(line).append(" ");
                }

                flushParagraph(blocks, sourceName, paragraph);
            }
        }

        @Override
        public ParsedBlock getNextBlock() {
            return blocks.poll();
        }
    }

    private static final class MarkdownParseUtil implements BlockProvider {
        private final Queue<ParsedBlock> blocks = new LinkedList<>();
        private final int maxParaLength;

        private MarkdownParseUtil(Path path, String sourceName, int maxParaLength) throws IOException {
            this.maxParaLength = maxParaLength;

            String content = java.nio.file.Files.readString(path);
            Parser parser = Parser.builder().build();
            Node document = parser.parse(content);

            MarkdownSection root = buildMarkdownSectionTree(document);
            processMarkdownSection(root, sourceName, 0);
        }

        @Override
        public ParsedBlock getNextBlock() {
            return blocks.poll();
        }

        private void processMarkdownSection(MarkdownSection section, String ctx, int depth) {
            processMarkdownSubSections(section, ctx, depth);

            List<String> contentBlocks = getMarkdownContentBlocks(section, depth);
            StringBuilder paragraph = new StringBuilder();

            for (String content : contentBlocks) {
                appendChunkOrFlush(blocks, ctx, paragraph, content, maxParaLength);
            }

            flushParagraph(blocks, ctx, paragraph);
        }

        private void processMarkdownSubSections(MarkdownSection section, String ctx, int depth) {
            if (depth >= 2) {
                return;
            }

            for (MarkdownSection child : section.children) {
                processMarkdownSection(child, ctx + "::" + child.title, depth + 1);
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

    private static final class AsciiDocParseUtil implements BlockProvider {
        private final Queue<ParsedBlock> blocks = new LinkedList<>();
        private final int maxParaLength;

        private AsciiDocParseUtil(Path path, String sourceName, int maxParaLength) throws IOException {
            this.maxParaLength = maxParaLength;

            String content = java.nio.file.Files.readString(path);
            Asciidoctor asciidoctor = Asciidoctor.Factory.create();
            org.asciidoctor.ast.Document document = asciidoctor.load(
                    content,
                    org.asciidoctor.Options.builder().build());

            processAsciiDocNode(document, sourceName, 0);
        }

        @Override
        public ParsedBlock getNextBlock() {
            return blocks.poll();
        }

        private void processAsciiDocNode(StructuralNode node, String ctx, int depth) {
            processSubSections(node, ctx, depth);

            Stream<StructuralNode> contentBlocks = getContentBlocks(node, depth);
            StringBuilder paragraph = new StringBuilder();

            contentBlocks.forEach(block -> {
                String content = extractBlockContent(block);
                appendChunkOrFlush(blocks, ctx, paragraph, content, maxParaLength);
            });

            flushParagraph(blocks, ctx, paragraph);
        }

        private void processSubSections(StructuralNode node, String ctx, int depth) {
            if (depth >= 2) {
                return;
            }

            List<StructuralNode> subSections = node.getBlocks().stream()
                    .filter(b -> b instanceof Section)
                    .toList();

            for (StructuralNode subSection : subSections) {
                processAsciiDocNode(subSection, ctx + "::" + subSection.getTitle(), depth + 1);
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

        private Stream<StructuralNode> flatten(StructuralNode node) {
            if (!(node instanceof Section) || node.getBlocks() == null || node.getBlocks().isEmpty()) {
                return Stream.empty();
            }

            return node.getBlocks().stream()
                    .flatMap(child -> Stream.concat(
                            Stream.of(child),
                            flatten(child)));
        }
    }
}