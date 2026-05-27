package com.llmcr.feature.sync.etl;

import com.llmcr.domain.entity.Chunk;
import com.llmcr.domain.entity.ChunkCollection;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.repository.ChunkCollectionRepository;
import com.llmcr.domain.repository.ContextRepository;
import com.llmcr.infrastructure.vectorstore.MyVectorStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoadService {

  private static final Logger logger = LoggerFactory.getLogger(LoadService.class);

  @PersistenceContext private EntityManager entityManager;

  private final ChunkCollectionRepository chunkCollectionRepository;
  private final ContextRepository contextRepository;
  private final MyVectorStore vectorStore;

  public LoadService(
      ChunkCollectionRepository chunkCollectionRepository,
      ContextRepository contextRepository,
      MyVectorStore vectorStore) {
    this.chunkCollectionRepository = chunkCollectionRepository;
    this.contextRepository = contextRepository;
    this.vectorStore = vectorStore;
  }

  /**
   * Load the chunks of a context to the vector store under the collections defined by the rack root
   * of the context's source.
   */
  @Transactional
  public void loadContext(Long contextId) {
    Context context =
        contextRepository
            .findById(contextId)
            .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));
    if (context.isChunkLoaded()) {
      logger.info("Context '{}' is already loaded, skipping", context.getName(), context.getId());
      return;
    }

    // load chunks into vector store
    Set<ChunkCollection> targetCollections = context.getSource().getTrackRoot().getInCollections();
    for (ChunkCollection chunkCollection : targetCollections) {
      // only load chunks that are not already in the collection to avoid duplication
      // in vector store
      Set<Chunk> collectionChunks = chunkCollection.getChunks();
      List<Chunk> chunksToAdd =
          context.getChunks().stream().filter(chunk -> collectionChunks.contains(chunk)).toList();

      if (!chunksToAdd.isEmpty()) {
        vectorStore.addChunks(chunksToAdd, chunkCollection.getName());
        logger.info(
            "Loaded {} chunks of context '{}' to collection '{}'",
            chunksToAdd.size(),
            context.getName(),
            chunkCollection.getName());
      }
    }

    context.setChunkLoaded(true);
    contextRepository.save(context);
  }

  @Transactional
  public void reloadCollection(String collectionName) {
    logger.info("Reloading chunk collection '{}'...", collectionName);
    ChunkCollection collection =
        chunkCollectionRepository
            .findByName(collectionName)
            .orElseThrow(
                () -> new RuntimeException("Chunk collection not found: " + collectionName));
    collection.clearChunks();
    vectorStore.removeCollection(collectionName);

    Set<Chunk> chunks =
        collection.getTrackRoots().stream()
            .flatMap(trackRoot -> trackRoot.getSources().stream())
            .flatMap(source -> source.getContexts().stream())
            .flatMap(context -> context.getChunks().stream())
            .collect(java.util.stream.Collectors.toSet());

    collection.addChunks(chunks);
    vectorStore.addChunks(new ArrayList<>(chunks), collectionName);
    logger.info("Reloaded chunk collection '{}', total {} chunks", collectionName, chunks.size());
  }

  /**
   * This method do the following: 1. Recompute what chunks should be in the collection based on the
   * track roots in the collection and the contexts related to those track roots. 2. Reload the
   * chunks into the vector store by removing all existing chunks of the collection from the vector
   * store and adding the new chunks to the vector store.
   */
  @Transactional
  public void reloadAllCollections() {
    logger.info("Start reloading chunks to vector store...");

    List<ChunkCollection> chunkCollections = chunkCollectionRepository.findAll();
    if (chunkCollections.isEmpty()) {
      logger.info("No chunk collections found, skipping reload");
      return;
    }

    // reset collections in both database and vector store
    chunkCollections.forEach(
        collection -> {
          vectorStore.removeCollection(collection.getName());
          logger.info("Cleared collection '{}' from vectorstore", collection.getName());
        });

    logger.info("Reloading chunks into vector store...");
    int totalAdded = 0;
    int totalSkippedMissingEmbedding = 0;
    for (ChunkCollection chunkCollection : chunkCollections) {
      String collectionName = chunkCollection.getName();

      Set<Chunk> collectionChunks = chunkCollection.getChunks();
      List<Chunk> chunksToLoad =
          collectionChunks.stream()
              .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
              .toList();

      int skippedMissingEmbedding = collectionChunks.size() - chunksToLoad.size();
      if (!chunksToLoad.isEmpty()) {
        vectorStore.addChunks(chunksToLoad, collectionName);
      }

      totalAdded += chunksToLoad.size();
      totalSkippedMissingEmbedding += skippedMissingEmbedding;

      logger.info(
          "Loaded {} chunks into collection '{}', skipped {} chunks without embedding",
          chunksToLoad.size(),
          collectionName,
          skippedMissingEmbedding);
    }

    logger.info(
        "Vector store reload complete. Added {} chunks, skipped {} chunks without embedding",
        totalAdded,
        totalSkippedMissingEmbedding);
  }

  @Transactional
  public int rebuildCollectionChunkMapping() {
    logger.info("Rebuilding collection-chunk mapping...");

    entityManager.createNativeQuery("DELETE FROM collection_have_chunks").executeUpdate();

    String sql =
        """
                INSERT INTO collection_have_chunks (chunk_collection_id, chunk_id)
                SELECT DISTINCT
                    ctr.chunk_collection_id,
                    ch.id
                FROM collection_have_track_roots ctr
                JOIN source s
                    ON s.track_root_id = ctr.track_root_id
                JOIN context c
                    ON c.source_id = s.id
                JOIN chunk ch
                    ON ch.context_id = c.id
                """;

    int inserted = entityManager.createNativeQuery(sql).executeUpdate();
    entityManager.flush();
    return inserted;
  }
}
