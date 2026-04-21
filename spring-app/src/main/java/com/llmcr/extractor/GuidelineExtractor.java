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
import com.llmcr.entity.contextImpl.GuidelineContext;

public class GuidelineExtractor implements ContextExtractor<GuidelineContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuidelineExtractor.class);

    @Override
    public boolean supports(Source source) {
        return source.getType() == Source.SourceType.XML;
    }

    @Override
    public List<GuidelineContext> apply(Source source) {
        Path path = Path.of(source.getPath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            LOGGER.warn("Guideline source path does not exist or is not a file: {}", path);
            return List.of();
        }

        try {
            Document document = parseXml(path);
            Element root = document.getDocumentElement();

            if (root == null || !"guidelines".equals(root.getTagName())) {
                return List.of();
            }

            List<GuidelineContext> result = new ArrayList<>();
            NodeList guidelineNodes = root.getElementsByTagName("guideline");
            String guidelineCtx = source.getSourceName();

            for (int i = 0; i < guidelineNodes.getLength(); i++) {
                Node node = guidelineNodes.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element guidelineElement = (Element) node;
                String content = getDirectChildText(guidelineElement, "content");
                if (content.isBlank()) {
                    continue;
                }

                result.add(new GuidelineContext(source, guidelineCtx, content, result.size()));
            }

            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse guideline XML {}: {}", path, e.getMessage());
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

}
