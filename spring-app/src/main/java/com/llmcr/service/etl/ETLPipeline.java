package com.llmcr.service.etl;

import java.util.List;

import org.springframework.ai.document.Document;

import com.llmcr.repository.*;
import com.llmcr.service.etl.operation.*;

public class ETLPipeline {

    private TrackRootRepository trackRootRepository;
    private SourceRepository sourceRepository;
    private ContextRepository contextRepository;

    public void run() {

        ExtractOperation extractOperation = new ExtractOperation();

        sourceRepository.findAll().forEach(source -> {
            List<Document> docs = extractOperation.execute(source);

            docs.forEach(doc -> {
                


                contextRepository.save(new Context(doc.get))
            });
        });

    }
}
