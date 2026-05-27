package com.llmcr.feature.sync.etl.transformer;

import com.llmcr.domain.entity.Chunk;
import com.llmcr.domain.entity.Context;
import com.llmcr.domain.util.TextCleaner;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

/** Split the content of a Context into multiple Chunks */
@Component
public class ContextContentSplitter implements ContextSplitter {

  @Override
  public boolean supports(Context context) {
    return true;
  }

  @Override
  public Context apply(Context context) {
    TokenTextSplitter splitter =
        TokenTextSplitter.builder()
            .withChunkSize(400)
            .withMinChunkSizeChars(200)
            .withMinChunkLengthToEmbed(10)
            .build();

    // split the content into chunks
    List<Chunk> contentChunks =
        splitter.split(new Document(TextCleaner.clean(context.getContent()))).stream()
            .map(doc -> new Chunk(doc.getText()))
            .toList();

    // add chunks back to context
    contentChunks.forEach(context::addChunk);

    return context;
  }
}
