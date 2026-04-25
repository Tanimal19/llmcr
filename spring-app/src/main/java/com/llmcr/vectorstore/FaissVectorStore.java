package com.llmcr.vectorstore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.llmcr.entity.Chunk;
import com.llmcr.model.EmbeddingClient;
import com.llmcr.service.FaissService;
import com.llmcr.service.FaissService.AddVectorsRequest;
import com.llmcr.service.FaissService.SearchVectorsRequest;
import com.llmcr.service.FaissService.SearchVectorsResponse;
import com.llmcr.service.rag.retrieval.ContextRetriever.ChunkScorePair;

@Repository
public class FaissVectorStore extends MyVectorStore {

    private static final int MAX_QUERY_LENGTH = 8192;

    private final FaissService faissService;
    private final EmbeddingClient embeddingModel;

    public FaissVectorStore(FaissService faissService, EmbeddingClient embeddingModel) {
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

    protected List<ChunkScorePair> doSimilaritySearch(String query, int topK, String collectionName) {
        float[] queryVector = embeddingModel.embed(truncateQuery(query));
        SearchVectorsResponse res = faissService.searchVectors(
                new SearchVectorsRequest(collectionName, queryVector, topK));

        assert res.ids().size() == res.scores().size() : "FAISS response ids and scores size mismatch";

        List<ChunkScorePair> chunks = new ArrayList<>(res.ids().size());
        for (int i = 0; i < res.ids().size(); i++) {
            chunks.add(new ChunkScorePair(res.ids().get(i), res.scores().get(i)));
        }

        return chunks;
    }

    private String truncateQuery(String query) {
        if (query == null || query.length() <= MAX_QUERY_LENGTH) {
            return query;
        }
        String truncated = query.substring(0, MAX_QUERY_LENGTH);
        return truncated;
    }
}
