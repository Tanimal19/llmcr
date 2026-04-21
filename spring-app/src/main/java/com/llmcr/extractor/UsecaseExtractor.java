package com.llmcr.extractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.llmcr.entity.Source;
import com.llmcr.entity.contextImpl.UsecaseContext;

public class UsecaseExtractor implements ContextExtractor<UsecaseContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsecaseExtractor.class);

    @Override
    public boolean supports(com.llmcr.entity.Source source) {
        return source.getSourceType() == Source.SourceType.XML;
    }

    @Override
    public List<UsecaseContext> apply(Source source) {
        Path path = Path.of(source.getSourcePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            LOGGER.warn("Usecase source path does not exist or is not a file: {}", path);
            return List.of();
        }

        try {
            Document document = parseXml(path);
            Element root = document.getDocumentElement();

            if (root == null || !"usecases".equals(root.getTagName())) {
                return List.of();
            }

            List<UsecaseContext> result = new ArrayList<>();
            NodeList usecaseNodes = root.getElementsByTagName("usecase");
            String usecaseCtx = source.getSourceName();

            for (int i = 0; i < usecaseNodes.getLength(); i++) {
                Node node = usecaseNodes.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element usecaseElement = (Element) node;
                String description = getDirectChildText(usecaseElement, "description");
                String input = getDirectChildText(usecaseElement, "input");
                String output = getDirectChildText(usecaseElement, "output");

                if (description.isBlank() && input.isBlank() && output.isBlank()) {
                    continue;
                }

                String content = buildContent(description, input, output);
                result.add(new UsecaseContext(source, usecaseCtx, content, result.size(), description));
            }

            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse usecase XML {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    private Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(path.toFile());
        document.getDocumentElement().normalize();
        return document;
    }

    private String getDirectChildText(Element parent, String childTagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && childTagName.equals(child.getNodeName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private String buildContent(String description, String input, String output) {
        StringBuilder builder = new StringBuilder();

        if (!description.isBlank()) {
            builder.append("Description:\n").append(description.trim()).append("\n\n");
        }
        if (!input.isBlank()) {
            builder.append("Input:\n").append(input.trim()).append("\n\n");
        }
        if (!output.isBlank()) {
            builder.append("Output:\n").append(output.trim());
        }

        return builder.toString().trim();
    }

}
