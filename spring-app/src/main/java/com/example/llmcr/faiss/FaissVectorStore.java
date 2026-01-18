package com.example.llmcr.faiss;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import com.example.llmcr.entity.Chunk;
import com.example.llmcr.faiss.FaissService.AddVectorsRequest;
import com.example.llmcr.faiss.FaissService.AddVectorsResponse;
import com.example.llmcr.faiss.FaissService.SearchVectorsRequest;
import com.example.llmcr.faiss.FaissService.SearchVectorsResponse;
import com.example.llmcr.repository.DataStore;

public class FaissVectorStore implements VectorStore {

    private final DataStore dataStore;
    private final FaissService faissService;
    private final EmbeddingModel embeddingModel;
    private final String indexName;

    public FaissVectorStore(DataStore dataStore, FaissService faissService, EmbeddingModel embeddingModel,
            String indexName) {
        this.dataStore = dataStore;
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
        dataStore.addAllChunksToIndexFileByIds(indexName, ids);

        // generate embeddings
        List<float[]> embeddings = documents.stream()
                .map(d -> embeddingModel.embed(d))
                .collect(Collectors.toList());

        // generate FAISS index
        AddVectorsRequest req = new AddVectorsRequest(indexName, ids, embeddings);

        AddVectorsResponse res = faissService.addVectors(req);
        System.out.println("Added " + res.added_count() + " vectors to index:" + indexName);
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
        float[] queryVector = embeddingModel.embed(request.getQuery());
        SearchVectorsRequest req = new SearchVectorsRequest(indexName, queryVector, request.getTopK());

        SearchVectorsResponse res = faissService.searchVectors(req);

        // Retrieve documents based on IDs and add similarity scores
        List<Long> ids = res.ids();
        List<Float> scores = res.scores();

        List<Document> documents = dataStore.findAllChunksByIds(ids).stream()
                .map(Chunk::toDocument)
                .collect(Collectors.toList());

        // Attach similarity scores to document metadata
        Map<Long, Float> idToScoreMap = new ConcurrentHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            idToScoreMap.put(ids.get(i), scores.get(i));
        }
        for (Document doc : documents) {
            Long chunkId = Long.parseLong(doc.getMetadata().get("chunk_id").toString());
            Float score = idToScoreMap.get(chunkId);
            doc.getMetadata().put("similarity_score", score);
        }

        return documents;
    }
}
