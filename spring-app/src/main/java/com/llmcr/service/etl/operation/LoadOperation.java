package com.llmcr.service.etl.operation;

import com.llmcr.entity.Context;
import com.llmcr.entity.contextImpl.ClassNodeContext;
import com.llmcr.extractor.ClassNodeExtractor;
import com.llmcr.extractor.ContextExtractor;
import com.llmcr.extractor.DocumentExtractor;
import com.llmcr.extractor.GuidelineExtractor;
import com.llmcr.extractor.UsecaseExtractor;

import java.util.List;
import java.util.Map;

import com.llmcr.entity.Chunk;

public class LoadOperation implements ETLOperation<Context, Chunk> {

    private Map<ContextExtractor<? extends Context>> extractors = List.of(
            new ClassNodeExtractor(),
            new DocumentExtractor(2000),
            new UsecaseExtractor(),
            new GuidelineExtractor());

    @Override
    public Chunk execute(Context context) {
    }
}
