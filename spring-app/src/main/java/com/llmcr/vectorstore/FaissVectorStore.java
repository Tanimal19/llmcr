package com.llmcr.vectorstore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.llmcr.entity.Chunk;
import com.llmcr.rag.retrieval.QueryContextRetriever.ChunkIdScorePair;
import com.llmcr.service.FaissService;
import com.llmcr.service.ModelClientFactory;
import com.llmcr.service.FaissService.AddVectorsRequest;
import com.llmcr.service.FaissService.SearchVectorsRequest;
import com.llmcr.service.FaissService.SearchVectorsResponse;

@Repository
public class FaissVectorStore extends MyVectorStore {

    private final FaissService faissService;
    private final EmbeddingModel embeddingModel;

    public FaissVectorStore(
            @Value("${llmcr.embedding.provider}") String embeddingProviderName,
            @Value("${llmcr.embedding.model}") String embeddingModelName,
            FaissService faissService,
            ModelClientFactory modelClientFactory) {
        this.faissService = faissService;
        this.embeddingModel = modelClientFactory.createEmbeddingModel(embeddingProviderName, embeddingModelName);
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
        float[] queryVector = embeddingModel.embed(query);
        SearchVectorsResponse res = faissService.searchVectors(
                new SearchVectorsRequest(collectionName, queryVector, topK));

        assert res.ids().size() == res.scores().size() : "FAISS response ids and scores size mismatch";

        List<ChunkIdScorePair> chunks = new ArrayList<>(res.ids().size());
        for (int i = 0; i < res.ids().size(); i++) {
            chunks.add(new ChunkIdScorePair(res.ids().get(i), res.scores().get(i)));
        }

        return chunks;
    }

    public void removeCollection(String collectionName) {
        faissService.removeIndex(new FaissService.RemoveIndexRequest(collectionName));
    }

    public void removeChunks(List<Long> chunkIds, String collectionName) {
        faissService.removeVectors(new FaissService.RemoveVectorsRequest(collectionName, chunkIds));
    }
}
