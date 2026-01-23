package com.example.llmcr.service.etl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.extractor.ClassNodeExtractor;
import com.example.llmcr.extractor.DocumentParagraphExtractor;
import com.example.llmcr.repository.DataStore;

public class ExtractService {

    private final DataStore dataStore;

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractService.class);

    public ExtractService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void extract(List<DataSource> rawDataSources, int maxParagraphLength) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Start data extraction");

        ClassNodeExtractor classNodeExtractor = new ClassNodeExtractor();
        DocumentParagraphExtractor documentParagraphExtractor = new DocumentParagraphExtractor(maxParagraphLength);

        // Iterate over all raw data sources and extract data
        rawDataSources.stream().forEach(source -> {
            List<ClassNode> classNodes = source.accept(classNodeExtractor);
            dataStore.saveAllClassNodes(classNodes);
            LOGGER.info("Extracted " + classNodes.size() + " class nodes from source: " + source.getSourceName());

            List<DocumentParagraph> paragraphs = source.accept(documentParagraphExtractor);
            dataStore.saveAllDocumentParagraphs(paragraphs);
            LOGGER.info(
                    "Extracted " + paragraphs.size() + " document paragraphs from source: " + source.getSourceName());
        });

        long endTime = System.currentTimeMillis();
        LOGGER.info("Data extraction completed in " + (endTime - startTime) + "ms");
    }
}
