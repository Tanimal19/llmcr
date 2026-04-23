package com.llmcr.service.rag.retrieval.fusion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.llmcr.entity.Context;
import com.llmcr.service.rag.retrieval.ContextRetriever.ContextScorePair;

/**
 * Use Reciprocal Rank Fusion (RRF) to fuse multiple lists of contexts.
 */
public class RankFusionStrategy implements FusionStrategy {
    final int RRF_K = 60;

    public List<ContextScorePair> fuse(List<List<ContextScorePair>> contextLists, int topK) {

        Map<Context, Double> contextMap = new HashMap<>();

        for (List<ContextScorePair> contexts : contextLists) {
            for (int rank = 0; rank < contexts.size(); rank++) {
                Context c = contexts.get(rank).context();
                double contribution = 1.0 / (RRF_K + rank + 1);
                contextMap.put(c, contextMap.getOrDefault(c, 0.0) + contribution);
            }
        }

        return contextMap.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(topK)
                .map(e -> new ContextScorePair(e.getKey(), e.getValue().floatValue()))
                .collect(Collectors.toList());
    }
}
