package com.example.llmcr.service.rag.retrieval.fusion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;

// Reciprocal Rank Fusion (RRF)
public class RankFusionStrategy implements FusionStrategy {
    final int RRF_K = 60;

    public List<Document> fuse(List<List<Document>> documentsLists, int topK) {

        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        for (List<Document> docs : documentsLists) {
            for (int rank = 0; rank < docs.size(); rank++) {
                Document d = docs.get(rank);
                String id = d.getMetadata().get("source_id").toString();

                docMap.putIfAbsent(id, d);

                double contribution = 1.0 / (RRF_K + rank + 1);
                scores.merge(id, contribution, Double::sum);
            }
        }

        List<Document> selectedDocs = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .collect(Collectors.toList());

        // Annotate documents with their RRF scores
        selectedDocs.forEach(doc -> {
            String id = doc.getMetadata().get("source_id").toString();
            doc.getMetadata().put("rrf_score", scores.get(id));
        });

        return selectedDocs;
    }
}
