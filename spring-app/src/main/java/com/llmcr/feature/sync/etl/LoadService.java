package com.llmcr.feature.sync.etl;

import com.llmcr.domain.entity.Chunk;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.repository.ContextRepository;
import com.llmcr.infrastructure.vectorstore.MyVectorStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoadService {

  private static final Logger logger = LoggerFactory.getLogger(LoadService.class);

  private final ContextRepository contextRepository;
  private final MyVectorStore vectorStore;

  public LoadService(ContextRepository contextRepository, MyVectorStore vectorStore) {
    this.contextRepository = contextRepository;
    this.vectorStore = vectorStore;
  }

  /** Embed the chunks of a context and add them to the FAISS index. */
  @Transactional
  public void loadContext(Long contextId) {
    Context context =
        contextRepository
            .findById(contextId)
            .orElseThrow(() -> new RuntimeException("Context not found: " + contextId));

    if (context.isChunkLoaded()) {
      logger.info("Context '{}' already loaded, skipping", context.getName());
      return;
    }

    vectorStore.addContexts(List.of(context));
    context.setChunkLoaded(true);
    contextRepository.save(context);
    logger.info("Loaded {} chunks for context '{}'", context.getChunks().size(), context.getName());
  }

  /**
   * Clear the FAISS index and reload all contexts whose chunks already have embeddings. Contexts
   * without embeddings are skipped and will be loaded when their ETL pipeline runs.
   */
  @Transactional
  public void reloadAllContexts() {
    logger.info("Reloading all contexts into vector store...");

    vectorStore.clearAll();

    List<Context> all = contextRepository.findAll();
    List<Context> withEmbeddings =
        all.stream()
            .filter(
                c ->
                    c.getChunks().stream()
                        .anyMatch(ch -> ch.getEmbedding() != null && ch.getEmbedding().length > 0))
            .toList();

    int skipped = all.size() - withEmbeddings.size();

    if (!withEmbeddings.isEmpty()) {
      vectorStore.addContexts(withEmbeddings);
    }

    logger.info(
        "Vector store reload complete. Loaded {} contexts, skipped {} without embeddings",
        withEmbeddings.size(),
        skipped);
  }
}
