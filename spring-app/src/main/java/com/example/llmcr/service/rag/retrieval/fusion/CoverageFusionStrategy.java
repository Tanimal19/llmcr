package com.example.llmcr.service.rag.retrieval.fusion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;

// Fusion by coverage: prioritize documents that cover more queries
public class CoverageFusionStrategy implements FusionStrategy {
    protected class DocStats {
        Document doc;
        int hitCount = 0;
        int bestRank = Integer.MAX_VALUE;

        protected DocStats(Document doc) {
            this.doc = doc;
        }
    }

    public List<Document> fuse(List<List<Document>> documentsLists, int topK) {
        Map<String, DocStats> statsMap = new HashMap<>();

        for (int q = 0; q < documentsLists.size(); q++) {
            List<Document> docs = documentsLists.get(q);

            for (int rank = 0; rank < docs.size(); rank++) {
                Document d = docs.get(rank);
                String id = d.getMetadata().get("chunk_id").toString();

                DocStats stats = statsMap.computeIfAbsent(id, k -> {
                    DocStats s = new DocStats(d);
                    return s;
                });

                stats.hitCount++;
                stats.bestRank = Math.min(stats.bestRank, rank);
            }
        }

        System.out.println("CoverageFusionStrategy: StatsMap:\n" + statsMap);

        return statsMap.values().stream()
                .sorted((a, b) -> {
                    // 1. coverage desc
                    int cmp = Integer.compare(b.hitCount, a.hitCount);
                    if (cmp != 0)
                        return cmp;

                    // 2. best rank asc
                    return Integer.compare(a.bestRank, b.bestRank);
                })
                .limit(topK)
                .map(s -> s.doc)
                .collect(Collectors.toList());
    }

}
