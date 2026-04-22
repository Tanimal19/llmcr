package com.llmcr.service.faiss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.ChunkCollection;
import com.llmcr.entity.Context;
import com.llmcr.repository.ContextRepository;
import com.llmcr.repository.ChunkRepository;
import com.llmcr.repository.ChunkCollectionRepository;
import com.llmcr.service.faiss.FaissService.AddVectorsRequest;
import com.llmcr.service.faiss.FaissService.AddVectorsResponse;
import com.llmcr.service.faiss.FaissService.SearchVectorsRequest;
import com.llmcr.service.faiss.FaissService.SearchVectorsResponse;

public class FaissVectorStore {

    private static final Logger logger = LoggerFactory.getLogger(FaissVectorStore.class);

    private static final int MAX_QUERY_LENGTH = 8192;
    private final String collectionName;

    private final FaissService faissService;
    private final EmbeddingModel embeddingModel;
    private final ContextRepository contextRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkCollectionRepository chunkCollectionRepository;

    public FaissVectorStore(
            FaissService faissService,
            EmbeddingModel embeddingModel,
            ContextRepository contextRepository,
            ChunkRepository chunkRepository,
            ChunkCollectionRepository chunkCollectionRepository,
            String collectionName) {
        this.faissService = faissService;
        this.embeddingModel = embeddingModel;
        this.contextRepository = contextRepository;
        this.chunkRepository = chunkRepository;
        this.chunkCollectionRepository = chunkCollectionRepository;
        this.collectionName = collectionName;

        if (chunkCollectionRepository.getByName(collectionName).isEmpty()) {
            chunkCollectionRepository.save(new ChunkCollection(collectionName));
            logger.info("Created new chunk collection: " + collectionName);
        }
        logger.info("Initialized FaissVectorStore with collection: " + collectionName);
    }

    public void add(List<Chunk> documents) {
        if (documents.isEmpty()) {
            return;
        }

        ChunkCollection chunkCollection = chunkCollectionRepository.getByName(collectionName).get();

        // convert documents to context


        List<Long> ids = documents.stream().map(doc -> {
            return doc.getMetadata("chunk_id")
        }).collect(Collectors.toList());

        // generate embeddings
        List<float[]> embeddings = documents.stream()
                .map(d -> embeddingModel.embed(d))
                .collect(Collectors.toList());

        // generate FAISS index
        AddVectorsResponse res = faissService.addVectors(
                new AddVectorsRequest(collectionName, ids, embeddings));
        logger.info("Added " + res.added_count() + " chunks to collection: " + collectionName);
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
        SearchVectorsResponse res = faissService.searchVectors(
                new SearchVectorsRequest(collectionName, queryVector, request.getTopK()));

        List<Long> chunkIds = res.ids();
        List<Float> chunkScores = res.scores();
        Map<Long, Float> idToScore = new HashMap<>();
        for (int i = 0; i < chunkIds.size(); i++) {
            idToScore.put(chunkIds.get(i), chunkScores.get(i));
        }

        // retrieve context using chunks
        class ContextHolder {
            List<Long> chunkIds = new ArrayList<>();
            float score = 0f;
        }
        Map<Context, ContextHolder> contextMap = new HashMap<>();

        logger.info("Retrieved chunks from datastore:");
        chunkIds.stream().forEach(id -> {
            Chunk chunk = chunkRepository.findById(id).orElse(null);
            Context context = chunk.getContext();
            logger.info("Chunk id:" + chunk.getId() +
                    ", score:" + idToScore.get(chunk.getId()) +
                    ", source context:" + context.getName());

            ContextHolder holder = contextMap.computeIfAbsent(context, k -> new ContextHolder());
            holder.chunkIds.add(chunk.getId());
            // if a context has multiple chunks in the search results, take the max score as
            // the context score
            holder.score = Math.max(holder.score, idToScore.get(chunk.getId()));
        });

        // convert to documents
        List<Document> documents = contextMap.keySet().stream()
                .map(context -> {
                    Document doc = Document.builder().text(context.getContent())
                            .metadata("context", context)
                            .score((double) contextMap.get(context).score)
                            .build();
                    return doc;
                })
                .collect(Collectors.toList());

        long endTime = System.currentTimeMillis();
        logger.info("Similarity search completed in " + (endTime - startTime) + "ms");

        return documents;
    }

    private String truncateQuery(String query) {
        if (query == null || query.length() <= MAX_QUERY_LENGTH) {
            return query;
        }

        String truncated = query.substring(0, MAX_QUERY_LENGTH);
        logger.warn("Query truncated from " + query.length() + " to " + MAX_QUERY_LENGTH + " characters");
        return truncated;
    }
}
