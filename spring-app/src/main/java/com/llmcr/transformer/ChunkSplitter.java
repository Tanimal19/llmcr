package com.llmcr.transformer;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import com.llmcr.repository.ChunkRepository;

public class ChunkSplitter implements DocumentTransformer {

    private ChunkRepository chunkRepository;

    public ChunkSplitter(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }
}
