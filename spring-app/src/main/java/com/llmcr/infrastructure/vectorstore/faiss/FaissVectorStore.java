package com.llmcr.infrastructure.vectorstore.faiss;

import com.llmcr.config.provider.EmbeddingModelConfigProvider;
import com.llmcr.domain.entity.Chunk;
import com.llmcr.domain.repository.ChunkRepository;
import com.llmcr.infrastructure.ai.ModelClientFactory;
import com.llmcr.infrastructure.vectorstore.ChunkIdScorePair;
import com.llmcr.infrastructure.vectorstore.MyVectorStore;
import com.llmcr.infrastructure.vectorstore.faiss.FaissService.AddVectorsRequest;
import com.llmcr.infrastructure.vectorstore.faiss.FaissService.SearchVectorsRequest;
import com.llmcr.infrastructure.vectorstore.faiss.FaissService.SearchVectorsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Repository;

@Repository
public class FaissVectorStore extends MyVectorStore {

    private final FaissService faissService;
    private final ChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;

    public FaissVectorStore(
            FaissService faissService,
            EmbeddingModelConfigProvider configProvider,
            ChunkRepository chunkRepository,
            ModelClientFactory modelClientFactory) {
        this.faissService = faissService;
        this.chunkRepository = chunkRepository;
        this.embeddingModel = modelClientFactory.createEmbeddingModel(configProvider.getEmbeddingModelConfig());
    }

    public void addChunks(List<Chunk> chunks, String collectionName) {
        if (chunks.isEmpty()) {
            return;
        }

        // generate embeddings for chunks that don't have embedding yet, and save to db
        for (Chunk chunk : chunks) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
                chunk.setEmbedding(embeddingModel.embed(chunk.getContent()));
            }
            chunkRepository.save(chunk);
        }

        // get ids and embeddings for all chunks
        List<Long> ids = chunks.stream().map(Chunk::getId).collect(Collectors.toList());
        List<float[]> embeddings = chunks
                .stream()
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
