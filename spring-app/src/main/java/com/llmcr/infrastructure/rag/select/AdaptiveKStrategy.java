package com.llmcr.infrastructure.rag.select;

import com.llmcr.infrastructure.rag.ContextScorePair;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AdaptiveKStrategy dynamically determines the optimal number of contexts to select based on the
 * score distribution. It looks for the largest gap in scores to find a natural cutoff point, while
 * also applying a buffer to include some additional contexts for robustness.
 */
public class AdaptiveKStrategy implements TopKSelectionStrategy {

  private static final Logger logger = LoggerFactory.getLogger(AdaptiveKStrategy.class);

  private static final int BUFFER = 5;
  private static final float HIGH_CONF_SCORE = 0.7f;
  private static final float LOW_CONF_SCORE = 0.3f;

  public List<ContextScorePair> select(List<ContextScorePair> contexts, int topK) {
    float maxGap = 0;
    int optimalK = 0;
    for (int i = 0; i < contexts.size() - 1; i++) {
      float score1 = contexts.get(i).score();
      if (score1 >= HIGH_CONF_SCORE) {
        optimalK = i + 1;
        continue; // always include high-confidence documents
      } else if (score1 < LOW_CONF_SCORE) {
        break; // drop all low-confidence documents
      }

      float score2 = contexts.get(i + 1).score();
      float gap = score1 - score2;
      if (gap > maxGap) {
        maxGap = gap;
        optimalK = i + 1;
      }
    }

    logger.debug("AdaptiveKStrategy determined optimalK: {}, maxGap: {:.4f}", optimalK, maxGap);
    logger.debug("Selected topK with buffer: {}", Math.min(optimalK + BUFFER, topK));

    return contexts.subList(0, Math.min(optimalK + BUFFER, topK));
  }
}
