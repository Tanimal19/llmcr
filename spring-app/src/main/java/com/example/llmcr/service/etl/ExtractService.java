package com.example.llmcr.service.etl;

import java.util.List;

import com.example.llmcr.datasource.DataSource;
import com.example.llmcr.entity.ClassNode;
import com.example.llmcr.entity.DocumentParagraph;
import com.example.llmcr.extractor.ClassNodeExtractor;
import com.example.llmcr.extractor.DocumentParagraphExtractor;
import com.example.llmcr.repository.DataStore;

public class ExtractService {

    private final DataStore dataStore;

    public ExtractService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void extract(List<DataSource> rawDataSources, int maxParagraphLength) {
        long startTime = System.currentTimeMillis();
        System.out.println("+ Starting data extraction...");
        System.out.println(
                "  - maxParagraphLength: " + maxParagraphLength);

        ClassNodeExtractor classNodeExtractor = new ClassNodeExtractor();
        DocumentParagraphExtractor documentParagraphExtractor = new DocumentParagraphExtractor(maxParagraphLength);

        // Iterate over all raw data sources and extract data
        rawDataSources.stream().forEach(source -> {
            List<ClassNode> classNodes = source.accept(classNodeExtractor);
            dataStore.saveAllClassNodes(classNodes);

            List<DocumentParagraph> paragraphs = source.accept(documentParagraphExtractor);
            dataStore.saveAllDocumentParagraphs(paragraphs);
        });

        long endTime = System.currentTimeMillis();
        System.out.println("+ Extraction completed in " + (endTime - startTime) + "ms");
    }
}
