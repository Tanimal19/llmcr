package com.llmcr.infrastructure.vectorstore.faiss;

import com.llmcr.config.provider.EmbeddingModelConfigProvider;
import com.llmcr.domain.entity.Chunk;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.repository.ChunkRepository;
import com.llmcr.domain.repository.ContextRepository;
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

  private static final String MAIN_INDEX = "main";

  private final FaissService faissService;
  private final ChunkRepository chunkRepository;
  private final EmbeddingModel embeddingModel;

  public FaissVectorStore(
      FaissService faissService,
      EmbeddingModelConfigProvider configProvider,
      ChunkRepository chunkRepository,
      ContextRepository contextRepository,
      ModelClientFactory modelClientFactory) {
    super(contextRepository);
    this.faissService = faissService;
    this.chunkRepository = chunkRepository;
    this.embeddingModel =
        modelClientFactory.createEmbeddingModel(configProvider.getEmbeddingModelConfig());
  }

  @Override
  public void addContexts(List<Context> contexts) {
    List<Chunk> chunksToAdd = new ArrayList<>();
    for (Context context : contexts) {
      for (Chunk chunk : context.getChunks()) {
        if (chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
          chunk.setEmbedding(embeddingModel.embed(chunk.getContent()));
          chunkRepository.save(chunk);
        }
        chunksToAdd.add(chunk);
      }
    }

    if (chunksToAdd.isEmpty()) {
      return;
    }

    List<Long> ids = chunksToAdd.stream().map(Chunk::getId).collect(Collectors.toList());
    List<float[]> embeddings =
        chunksToAdd.stream().map(Chunk::getEmbedding).collect(Collectors.toList());

    faissService.addVectors(new AddVectorsRequest(MAIN_INDEX, ids, embeddings));
  }

  @Override
  protected List<ChunkIdScorePair> doSearch(
      String query, int topK, List<Long> allowedChunkIds) {
    float[] queryVector = embeddingModel.embed(query);
    SearchVectorsResponse res =
        faissService.searchVectors(new SearchVectorsRequest(MAIN_INDEX, queryVector, topK, allowedChunkIds));

    assert res.ids().size() == res.scores().size() : "FAISS response ids and scores size mismatch";

    List<ChunkIdScorePair> result = new ArrayList<>(res.ids().size());
    for (int i = 0; i < res.ids().size(); i++) {
      result.add(new ChunkIdScorePair(res.ids().get(i), res.scores().get(i)));
    }
    return result;
  }

  @Override
  public void removeContexts(List<Long> contextIds) {
    if (contextIds.isEmpty()) {
      return;
    }
    List<Long> chunkIds = chunkRepository.findIdsByContextIdIn(contextIds);
    if (!chunkIds.isEmpty()) {
      faissService.removeVectors(new FaissService.RemoveVectorsRequest(MAIN_INDEX, chunkIds));
    }
  }

  @Override
  public void clearAll() {
    faissService.removeIndex(new FaissService.RemoveIndexRequest(MAIN_INDEX));
  }
}
