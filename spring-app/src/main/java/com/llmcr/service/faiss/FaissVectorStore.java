package com.llmcr.service.faiss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import com.llmcr.entity.Source;
import com.llmcr.repository.ContextRepository;
import com.llmcr.repository.VectorStoreRepository;
import com.llmcr.service.faiss.FaissService.AddVectorsRequest;
import com.llmcr.service.faiss.FaissService.AddVectorsResponse;
import com.llmcr.service.faiss.FaissService.SearchVectorsRequest;
import com.llmcr.service.faiss.FaissService.SearchVectorsResponse;

public class FaissVectorStore implements VectorStore {

    private static final int MAX_QUERY_LENGTH = 8000;

    private final ContextRepository contextRepository;
    private final VectorStoreRepository vectorStoreRepository;

    private final FaissService faissService;
    private final EmbeddingModel embeddingModel;
    private final String indexName;

    private static final Logger LOGGER = LoggerFactory.getLogger(FaissVectorStore.class);

    public FaissVectorStore(
            FaissService faissService, EmbeddingModel embeddingModel,
            String indexName) {
        this.faissService = faissService;
        this.embeddingModel = embeddingModel;
        this.indexName = indexName;

    }

    @Override
    public void add(List<Document> documents) {
        if (documents.isEmpty()) {
            return;
        }

        // update index file record
        List<Long> ids = documents.stream()
                .map(d -> (Long) d.getMetadata().get("chunk_id"))
                .collect(Collectors.toList());
        dataStore.addAllChunksToIndexSetByIds(indexName, ids);

        // generate embeddings
        List<float[]> embeddings = documents.stream()
                .map(d -> embeddingModel.embed(d))
                .collect(Collectors.toList());

        // generate FAISS index
        AddVectorsRequest req = new AddVectorsRequest(indexName, ids, embeddings);

        AddVectorsResponse res = faissService.addVectors(req);
        LOGGER.info("Added " + res.added_count() + " chunks to index:" +
                indexName);
    }

    @Override
    public void delete(List<String> idList) {
        // Not implemented yet
        throw new UnsupportedOperationException("Delete operation is not implemented");
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        // Not implemented yet
        throw new UnsupportedOperationException("Delete operation is not implemented");
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        String query = truncateQuery(request.getQuery());
        float[] queryVector = embeddingModel.embed(query);
        SearchVectorsRequest req = new SearchVectorsRequest(indexName, queryVector, request.getTopK());
        SearchVectorsResponse res = faissService.searchVectors(req);

        List<Long> chunkIds = res.ids();
        List<Float> chunkScores = res.scores();
        Map<Long, Float> idToScore = new HashMap<>();
        for (int i = 0; i < chunkIds.size(); i++) {
            idToScore.put(chunkIds.get(i), chunkScores.get(i));
        }

        // retrieve source documents using chunks
        class SourceHolder {
            List<Long> chunkIds = new ArrayList<>();
            float score = 0f;
        }
        Map<Source, SourceHolder> sourceMap = new HashMap<>();

        LOGGER.info("Retrieved chunks from datastore:");
        dataStore.findAllChunksByIds(chunkIds).stream().forEach(e -> {
            Source source = e.getSource();
            LOGGER.info("Chunk id:" + e.getId() +
                    ", score:" + idToScore.get(e.getId()) +
                    ", type:" + e.getContentType() +
                    ", source:" + source.getId());

            SourceHolder holder = sourceMap.computeIfAbsent(source, k -> new SourceHolder());
            holder.chunkIds.add(e.getId());
            holder.score = Math.max(holder.score, idToScore.get(e.getId()));
        });

        // convert to documents
        List<Document> documents = sourceMap.keySet().stream()
                .map(s -> {
                    Document doc = new Document(s.getContent());
                    doc.getMetadata().put("source_id", s.getId());
                    doc.getMetadata().put("source_name", s.getSourceName());
                    doc.getMetadata().put("chunk_ids", sourceMap.get(s).chunkIds);
                    doc.getMetadata().put("similarity_score", sourceMap.get(s).score);
                    return doc;
                })
                .collect(Collectors.toList());

        long endTime = System.currentTimeMillis();
        LOGGER.info("Similarity search completed in " + (endTime - startTime) + "ms");

        return documents;
    }

    private String truncateQuery(String query) {
        if (query == null || query.length() <= MAX_QUERY_LENGTH) {
            return query;
        }

        String truncated = query.substring(0, MAX_QUERY_LENGTH);
        LOGGER.warn("Query truncated from " + query.length() + " to " + MAX_QUERY_LENGTH + " characters");
        return truncated;
    }
}
