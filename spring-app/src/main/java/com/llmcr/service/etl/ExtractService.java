package com.llmcr.service.etl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.llmcr.entity.Source;
import com.llmcr.entity.contextImpl.*;
import com.llmcr.extractor.*;

public class ExtractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractService.class);

    private ExtractService() {
    }

    public static void extract(List<Source> source, int maxParagraphLength) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Start data extraction");

        ClassNodeExtractor classNodeExtractor = new ClassNodeExtractor();
        DocumentParagraphExtractor documentParagraphExtractor = new DocumentParagraphExtractor(maxParagraphLength);

        // Iterate over all raw data sources and extract data
        rawDataSources.stream().forEach(source -> {
            List<ClassNodeContext> classNodes = source.accept(classNodeExtractor);
            dataStore.saveAllClassNodes(classNodes);
            LOGGER.info("Extracted " + classNodes.size() + " class nodes from source: " + source.getSourceName());

            List<DocumentContext> paragraphs = source.accept(documentParagraphExtractor);
            dataStore.saveAllDocumentParagraphs(paragraphs);
            LOGGER.info(
                    "Extracted " + paragraphs.size() + " document paragraphs from source: " + source.getSourceName());
        });

        long endTime = System.currentTimeMillis();
        LOGGER.info("Data extraction completed in " + (endTime - startTime) + "ms");
    }
}
