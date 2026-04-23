package com.llmcr.service.rag.retrieval.rerank;

import java.util.List;

import com.llmcr.service.rag.retrieval.ContextRetriever.ChunkScorePair;

public class AdaptiveKStrategy implements RerankStrategy {

    private final int buffer = 5;
    private final float highConfidenceScore = 0.7f;
    private final float lowConfidenceScore = 0.3f;

    public List<ChunkScorePair> rerank(List<ChunkScorePair> chunks, int topK) {
        float maxGap = 0;
        int optimalK = 0;
        for (int i = 0; i < chunks.size() - 1; i++) {
            float score1 = chunks.get(i).score();
            if (score1 >= highConfidenceScore) {
                optimalK = i + 1;
                continue; // always include high-confidence documents
            } else if (score1 < lowConfidenceScore) {
                break; // drop all low-confidence documents
            }

            float score2 = chunks.get(i + 1).score();
            float gap = score1 - score2;
            if (gap > maxGap) {
                maxGap = gap;
                optimalK = i + 1;
            }
        }

        return chunks.subList(0, Math.min(optimalK + buffer, chunks.size()));
    }
}
