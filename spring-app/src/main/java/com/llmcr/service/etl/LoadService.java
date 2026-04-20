package com.llmcr.service.etl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.Chunk.ChunkContentType;

public class LoadService {

        private static final Logger LOGGER = LoggerFactory.getLogger(LoadService.class);

        private LoadService() {
        }

        public static void chunk(DataStore dataStore, TextSplitter splitter) {
                long startTime = System.currentTimeMillis();
                LOGGER.info("Start data chunking");

                // chunk all class nodes
                dataStore.findProcessedClassNodes().stream().forEach(node -> {
                        List<Document> docs = new ArrayList<>();

                        docs.add(new Document(textFilter(node.getContent()),
                                        Map.of("content_type", ChunkContentType.CODE, "source", node)));

                        docs.add(new Document(textFilter(node.getDescriptionText()),
                                        Map.of("content_type", ChunkContentType.ENRICHMENT, "source", node)));
                        docs.add(new Document(textFilter(node.getUsageText()),
                                        Map.of("content_type", ChunkContentType.ENRICHMENT, "source", node)));
                        docs.add(new Document(textFilter(node.getRelationshipText()),
                                        Map.of("content_type", ChunkContentType.ENRICHMENT, "source", node)));

                        List<Document> splitDocs = splitter.split(docs);
                        dataStore.saveAllChunksByDocuments(splitDocs);
                        LOGGER.info("Created " + splitDocs.size() + " chunks for ClassNode: "
                                        + node.getSignature());
                });

                // chunk all document paragraphs
                dataStore.findAllDocumentParagraphs().stream().forEach(paragraph -> {
                        DocumentContext doc = new DocumentContext(textFilter(paragraph.getContent()),
                                        Map.of("content_type", ChunkContentType.DOCUMENT, "source", paragraph));
                        List<DocumentContext> splitDocs = splitter.split(doc);
                        dataStore.saveAllChunksByDocuments(splitDocs);
                        LOGGER.info("Created " + splitDocs.size() + " chunks for DocumentParagraph: "
                                        + paragraph.getId());
                });

                long endTime = System.currentTimeMillis();
                LOGGER.info("Data chunking completed in " + (endTime - startTime) + "ms");
        }

        private static String textFilter(String text) {
                if (text == null) {
                        return "";
                }
                return text
                                // remove control characters except newlines nd tabs
                                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                                // specific cleaning for ANTLR serialized ATN
                                .replaceAll("_serializedATN\\s*=\\s*\"[\\s\\S]*?\";",
                                                "_serializedATN = \"<ANTLR_SERIALIZED_ATN>\";");
        }

        public static void load(DataStore dataStore, VectorStore vectorStore,
                        Set<ChunkContentType> loadedChunkTypes) {
                long startTime = System.currentTimeMillis();
                LOGGER.info("Start data loading");

                for (ChunkContentType type : loadedChunkTypes) {
                        List<Document> documents = dataStore.findAllChunksByContentType(type).stream()
                                        .map(Chunk::toDocument)
                                        .toList();

                        if (!documents.isEmpty()) {
                                vectorStore.add(documents);
                                LOGGER.info("+ Loaded " + documents.size() + " documents for type: " + type);
                        }
                }

                long endTime = System.currentTimeMillis();
                LOGGER.info("Data loading completed in " + (endTime - startTime) + "ms");
        }
}
