package com.example.llmcr.service.etl;

import java.util.List;
import java.util.logging.Logger;

import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.extractor.ClassNodeExtractor;
import com.example.llmcr.extractor.DocumentParagraphExtractor;
import com.example.llmcr.repository.DataStore;

public class ExtractService {

    private final DataStore dataStore;

    private static final Logger logger = Logger.getLogger(ExtractService.class.getName());

    public ExtractService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void extract(List<DataSource> rawDataSources, int maxParagraphLength) {
        long startTime = System.currentTimeMillis();
        logger.info("Start data extraction");

        ClassNodeExtractor classNodeExtractor = new ClassNodeExtractor();
        DocumentParagraphExtractor documentParagraphExtractor = new DocumentParagraphExtractor(maxParagraphLength);

        // Iterate over all raw data sources and extract data
        rawDataSources.stream().forEach(source -> {
            List<ClassNode> classNodes = source.accept(classNodeExtractor);
            dataStore.saveAllClassNodes(classNodes);
            logger.fine("Extracted " + classNodes.size() + " class nodes from source: " + source.getSourceName());

            List<DocumentParagraph> paragraphs = source.accept(documentParagraphExtractor);
            dataStore.saveAllDocumentParagraphs(paragraphs);
            logger.fine(
                    "Extracted " + paragraphs.size() + " document paragraphs from source: " + source.getSourceName());
        });

        long endTime = System.currentTimeMillis();
        logger.info("Data extraction completed in " + (endTime - startTime) + "ms");
    }
}
