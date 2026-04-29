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
import com.llmcr.service.rag.ContextRetriever.ChunkIdScorePair;

@Repository
public class FaissVectorStore extends MyVectorStore {

    private static final int MAX_QUERY_LENGTH = 8192;

    private final FaissService faissService;
    private final EmbeddingClient embeddingModel;

    public FaissVectorStore(FaissService faissService, EmbeddingClient embeddingModel) {
        this.faissService = faissService;
        this.embeddingModel = embeddingModel;
    }

    public void addChunks(List<Chunk> chunks, String collectionName) {
        if (chunks.isEmpty()) {
            return;
        }

        List<Long> ids = chunks.stream().map(Chunk::getId).collect(Collectors.toList());
        List<float[]> embeddings = chunks.stream()
                .map(chunk -> chunk.getEmbedding())
                .filter(embedding -> embedding != null)
                .collect(Collectors.toList());

        if (embeddings.size() != ids.size()) {
            throw new IllegalStateException("Some chunks are missing embeddings");
        }

        faissService.addVectors(new AddVectorsRequest(collectionName, ids, embeddings));
    }

    protected List<ChunkIdScorePair> doSimilaritySearch(String query, int topK, String collectionName) {
        float[] queryVector = embeddingModel.embed(truncateQuery(query));
        SearchVectorsResponse res = faissService.searchVectors(
                new SearchVectorsRequest(collectionName, queryVector, topK));

        assert res.ids().size() == res.scores().size() : "FAISS response ids and scores size mismatch";

        List<ChunkIdScorePair> chunks = new ArrayList<>(res.ids().size());
        for (int i = 0; i < res.ids().size(); i++) {
            chunks.add(new ChunkIdScorePair(res.ids().get(i), res.scores().get(i)));
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

    public void removeCollection(String collectionName) {
        faissService.removeIndex(new FaissService.RemoveIndexRequest(collectionName));
    }

    public void removeChunks(List<Long> chunkIds, String collectionName) {
        faissService.removeVectors(new FaissService.RemoveVectorsRequest(collectionName, chunkIds));
    }
}
