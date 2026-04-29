package com.llmcr.service.rag.retrieval.select;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.llmcr.service.rag.retrieval.ContextRetriever.ContextScorePair;

/**
 * AdaptiveKStrategy dynamically determines the optimal number of contexts to
 * select based on the score distribution. It looks for the largest gap in
 * scores to find a natural cutoff point, while also applying a buffer to
 * include some additional contexts for robustness.
 */
public class AdaptiveKStrategy implements SelectStrategy {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveKStrategy.class);

    private final int buffer = 5;
    private final float highConfidenceScore = 0.7f;
    private final float lowConfidenceScore = 0.3f;

    public List<ContextScorePair> select(List<ContextScorePair> contexts, int topK) {
        float maxGap = 0;
        int optimalK = 0;
        for (int i = 0; i < contexts.size() - 1; i++) {
            float score1 = contexts.get(i).score();
            if (score1 >= highConfidenceScore) {
                optimalK = i + 1;
                continue; // always include high-confidence documents
            } else if (score1 < lowConfidenceScore) {
                break; // drop all low-confidence documents
            }

            float score2 = contexts.get(i + 1).score();
            float gap = score1 - score2;
            if (gap > maxGap) {
                maxGap = gap;
                optimalK = i + 1;
            }
        }

        log.info("AdaptiveKStrategy determined optimalK: {}, maxGap: {:.4f}", optimalK, maxGap);

        return contexts.subList(0, Math.min(optimalK + buffer, topK));
    }
}
