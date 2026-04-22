package com.llmcr.service.etl.loader;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.llmcr.entity.Chunk;
import com.llmcr.entity.Context;
import com.llmcr.service.vectorstore.MyVectorStore;

public class VectorStoreLoader implements ContextLoader {

    private final MyVectorStore vectorStore;

    public VectorStoreLoader(MyVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void accept(List<Context> contexts) {

        Map<String, List<Chunk>> collectionToChunks = contexts.stream()
                .flatMap(c -> c.getChunks().stream())
                .flatMap(ch -> ch.getChunkCollections().stream()
                        .map(cc -> new AbstractMap.SimpleEntry<>(cc.getName(), ch)))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        collectionToChunks.forEach((collectionName, chunks) -> {
            vectorStore.add(chunks, collectionName);
        });
    }
}
