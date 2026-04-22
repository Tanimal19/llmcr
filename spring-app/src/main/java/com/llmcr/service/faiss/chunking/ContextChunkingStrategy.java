package com.llmcr.service.faiss.chunking;

import java.util.List;
import com.llmcr.entity.Context;
import com.llmcr.entity.Chunk;

public interface ContextChunkingStrategy<T extends Context> {
    List<Chunk> chunk(T context);
}
