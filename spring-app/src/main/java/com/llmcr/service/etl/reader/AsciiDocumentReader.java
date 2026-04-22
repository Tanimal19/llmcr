package com.llmcr.service.etl.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.jruby.ast.impl.ListItemImpl;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class AsciiDocumentReader implements DocumentReader {

    private static final int MAX_PARA_LENGTH = 2000;

    private final Resource asciiResource;

    public AsciiDocumentReader(String asciiSource) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(asciiSource);
            if (resources.length == 0) {
                throw new IllegalArgumentException("No resource found for: " + asciiSource);
            }
            this.asciiResource = resources[0];
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Document> get() {
        try {
            Path path = Path.of(this.asciiResource.getURI());
            String sourceName = this.asciiResource.getFilename();
            Asciidoctor asciidoctor = Asciidoctor.Factory.create();
            org.asciidoctor.ast.Document document = asciidoctor.load(
                    Files.readString(path), org.asciidoctor.Options.builder().build());

            List<Document> documents = new ArrayList<>();
            processNode(document, sourceName, 0, documents);
            return documents;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processNode(StructuralNode node, String ctx, int depth, List<Document> output) {
        if (depth < 2) {
            node.getBlocks().stream()
                    .filter(b -> b instanceof Section)
                    .forEach(s -> processNode(s, ctx + "::" + s.getTitle(), depth + 1, output));
        }

        Stream<StructuralNode> contentBlocks = depth < 2
                ? node.getBlocks().stream().filter(b -> !(b instanceof Section))
                : flatten(node);

        StringBuilder paragraph = new StringBuilder();
        contentBlocks.forEach(block -> appendOrFlush(output, ctx, paragraph, extractContent(block)));
        flush(output, ctx, paragraph);
    }

    private void appendOrFlush(List<Document> output, String ctx, StringBuilder paragraph, String content) {
        if (content == null || content.isBlank())
            return;
        if (paragraph.length() + content.length() > MAX_PARA_LENGTH && paragraph.length() > 0) {
            flush(output, ctx, paragraph);
        }
        paragraph.append(content).append("\n\n");
    }

    private void flush(List<Document> output, String ctx, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            output.add(Document.builder().text(paragraph.toString().trim()).metadata("source", ctx).build());
            paragraph.setLength(0);
        }
    }

    private String extractContent(StructuralNode block) {
        if (block instanceof Section s)
            return s.getTitle();
        if (block instanceof org.asciidoctor.ast.List list) {
            StringBuilder sb = new StringBuilder();
            for (StructuralNode item : list.getItems())
                sb.append(((ListItemImpl) item).getText()).append("\n");
            return sb.toString();
        }
        return block.getContent() == null ? null : block.getContent().toString();
    }

    private Stream<StructuralNode> flatten(StructuralNode node) {
        if (!(node instanceof Section) || node.getBlocks() == null || node.getBlocks().isEmpty())
            return Stream.empty();
        return node.getBlocks().stream()
                .flatMap(child -> Stream.concat(Stream.of(child), flatten(child)));
    }
}
