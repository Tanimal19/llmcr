package com.llmcr.service.vectorstore.faiss;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.llmcr.entity.Chunk;
import com.llmcr.service.vectorstore.MyVectorStore;
import com.llmcr.service.vectorstore.faiss.FaissService.AddVectorsRequest;
import com.llmcr.service.vectorstore.faiss.FaissService.SearchVectorsRequest;
import com.llmcr.service.vectorstore.faiss.FaissService.SearchVectorsResponse;

@Repository
public class FaissVectorStore extends MyVectorStore {

    private static final int MAX_QUERY_LENGTH = 8192;

    private final FaissService faissService;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public FaissVectorStore(FaissService faissService, EmbeddingModel embeddingModel) {
        this.faissService = faissService;
        this.embeddingModel = embeddingModel;
    }

    public void add(List<Chunk> chunks, String collectionName) {
        if (chunks.isEmpty()) {
            return;
        }

        List<Long> ids = chunks.stream().map(Chunk::getId).collect(Collectors.toList());
        List<float[]> embeddings = chunks.stream()
                .map(c -> embeddingModel.embed(c.getContent()))
                .collect(Collectors.toList());

        faissService.addVectors(new AddVectorsRequest(collectionName, ids, embeddings));
    }

    public List<ChunkWithScore> doSimilaritySearch(SearchRequest request, String collectionName) {
        String query = truncateQuery(request.getQuery());
        float[] queryVector = embeddingModel.embed(query);
        SearchVectorsResponse res = faissService.searchVectors(
                new SearchVectorsRequest(collectionName, queryVector, request.getTopK()));

        assert res.ids().size() == res.scores().size() : "FAISS response ids and scores size mismatch";

        List<ChunkWithScore> results = new ArrayList<>(res.ids().size());
        for (int i = 0; i < res.ids().size(); i++) {
            results.add(new ChunkWithScore(res.ids().get(i), res.scores().get(i)));
        }

        return results;
    }

    private String truncateQuery(String query) {
        if (query == null || query.length() <= MAX_QUERY_LENGTH) {
            return query;
        }
        String truncated = query.substring(0, MAX_QUERY_LENGTH);
        return truncated;
    }
}
